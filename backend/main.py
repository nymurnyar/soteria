"""
Soteria listener backend.

Flow
----
1. The Android app calls ``POST /sessions`` and gets a one-time 6-digit code.
2. The app dials the Soteria Twilio number (original call goes on hold) and sends
   the code as DTMF tones.
3. Twilio hits ``POST /twilio/voice`` -> we <Gather> the 6 digits.
4. ``POST /twilio/joined`` matches the code, marks the session "listening" and
   starts Twilio real-time transcription, then keeps the leg open silently.
5. The app sees "listening" and merges the calls into a 3-way conference.
6. Twilio posts transcription events to ``POST /twilio/transcription``.
7. Every few final sentences we send the conversation so far to Claude and ask
   "Is this a scam? How risky (0-100)? Which warning signs?" and save the answer
   on the session (``risk``).
8. The app polls ``GET /sessions/{code}`` for new transcript lines + the risk result.

Sessions are kept in memory -- fine for a prototype, use Redis/DB in production.
"""

import json
import os
import secrets
import threading
import time
from typing import Dict, Optional
from xml.sax.saxutils import quoteattr

import re

from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.responses import Response
from twilio.request_validator import RequestValidator

PUBLIC_BASE_URL = os.environ.get("PUBLIC_BASE_URL", "").rstrip("/")  # e.g. https://abc.ngrok.app
TWILIO_AUTH_TOKEN = os.environ.get("TWILIO_AUTH_TOKEN", "")
SOTERIA_API_KEY = os.environ.get("SOTERIA_API_KEY", "")
VALIDATE_TWILIO = os.environ.get("VALIDATE_TWILIO", "1") != "0"
LANGUAGE_CODE = os.environ.get("TRANSCRIPTION_LANGUAGE", "en-US")

ANTHROPIC_API_KEY = os.environ.get("ANTHROPIC_API_KEY", "")
ANTHROPIC_MODEL = os.environ.get("ANTHROPIC_MODEL", "claude-haiku-5-5")
ANALYZE_EVERY_LINES = int(os.environ.get("ANALYZE_EVERY_LINES", "2"))  # "every few sentences"

SESSION_TTL_SECONDS = 6 * 60 * 60
CODE_LENGTH = 6

app = FastAPI(title="Soteria listener")
_lock = threading.Lock()
_sessions: Dict[str, dict] = {}


# ---- Helpers ---------------------------------------------------------------------------

def _twiml(body: str) -> Response:
    return Response(
        content=f'<?xml version="1.0" encoding="UTF-8"?><Response>{body}</Response>',
        media_type="application/xml",
    )


def _purge_expired() -> None:
    now = time.time()
    for code in [c for c, s in _sessions.items() if now - s["created"] > SESSION_TTL_SECONDS]:
        del _sessions[code]


def _new_code() -> str:
    while True:
        code = "".join(secrets.choice("0123456789") for _ in range(CODE_LENGTH))
        if code not in _sessions:
            return code


def require_api_key(x_soteria_key: Optional[str] = Header(default=None)) -> None:
    """Protects the app-facing endpoints (codes are short, so don't leave them open)."""
    if SOTERIA_API_KEY and x_soteria_key != SOTERIA_API_KEY:
        raise HTTPException(status_code=401, detail="Invalid API key")


async def twilio_form(request: Request) -> dict:
    """Parses Twilio's form POST and verifies the X-Twilio-Signature header."""
    form = dict(await request.form())
    if VALIDATE_TWILIO:
        if not (TWILIO_AUTH_TOKEN and PUBLIC_BASE_URL):
            raise HTTPException(500, "Set TWILIO_AUTH_TOKEN and PUBLIC_BASE_URL (or VALIDATE_TWILIO=0)")
        url = PUBLIC_BASE_URL + request.url.path
        if request.url.query:
            url += "?" + request.url.query
        signature = request.headers.get("X-Twilio-Signature", "")
        if not RequestValidator(TWILIO_AUTH_TOKEN).validate(url, form, signature):
            raise HTTPException(403, "Invalid Twilio signature")
    return form


# ---- App-facing API ----------------------------------------------------------------------

@app.post("/sessions", dependencies=[Depends(require_api_key)])
def create_session() -> dict:
    with _lock:
        _purge_expired()
        code = _new_code()
        _sessions[code] = {
            "created": time.time(),
            "status": "waiting",  # waiting -> listening -> ended
            "call_sid": None,
            "lines": [],
            "partial": "",
            "risk": _empty_risk(),
            "analyzing": False,
            "analyzed_upto": 0,
        }
    return {"code": code}


@app.get("/sessions/{code}", dependencies=[Depends(require_api_key)])
def get_session(code: str, after: int = 0) -> dict:
    with _lock:
        session = _sessions.get(code)
        if session is None:
            raise HTTPException(404, "Unknown session")
        lines = session["lines"]
        after = max(0, min(after, len(lines)))
        return {
            "status": session["status"],
            "lines": lines[after:],
            "next": len(lines),
            "partial": session["partial"],
            "risk": session["risk"],
        }


@app.get("/health")
def health() -> dict:
    return {"ok": True}


# ---- Twilio webhooks ---------------------------------------------------------------------

@app.post("/twilio/voice")
async def twilio_voice(form: dict = Depends(twilio_form)) -> Response:
    """The Soteria number was dialled: wait silently for the 6-digit session code."""
    action = quoteattr(f"{PUBLIC_BASE_URL}/twilio/joined")
    return _twiml(
        f'<Gather input="dtmf" numDigits="{CODE_LENGTH}" timeout="15" action={action} method="POST"/>'
        "<Hangup/>"
    )


@app.post("/twilio/joined")
async def twilio_joined(form: dict = Depends(twilio_form)) -> Response:
    """Code received: start real-time transcription and keep the leg open."""
    code = form.get("Digits", "")
    with _lock:
        session = _sessions.get(code)
        if session is None or session["status"] != "waiting":
            return _twiml("<Hangup/>")
        session["status"] = "listening"
        session["call_sid"] = form.get("CallSid")

    callback = quoteattr(f"{PUBLIC_BASE_URL}/twilio/transcription?code={code}")
    # inbound_track = the audio coming *into* this leg, i.e. the mixed conference audio
    # (both the employee and the caller). The listener itself never speaks.
    return _twiml(
        "<Start>"
        f'<Transcription statusCallbackUrl={callback} track="inbound_track" '
        f'languageCode="{LANGUAGE_CODE}" partialResults="true"/>'
        "</Start>"
        '<Pause length="14400"/>'
    )


@app.post("/twilio/transcription")
async def twilio_transcription(code: str, form: dict = Depends(twilio_form)) -> dict:
    """Real-time transcription events from Twilio."""
    event = form.get("TranscriptionEvent", "")
    with _lock:
        session = _sessions.get(code)
        if session is None:
            return {"ok": True}
        if event == "transcription-content":
            try:
                text = json.loads(form.get("TranscriptionData", "{}")).get("transcript", "").strip()
            except ValueError:
                text = ""
            if form.get("Final", "false").lower() == "true":
                if text:
                    session["lines"].append(text)
                    if len(session["lines"]) - session["analyzed_upto"] >= ANALYZE_EVERY_LINES:
                        _start_analysis(code, session)
                session["partial"] = ""
            else:
                session["partial"] = text
        elif event in ("transcription-stopped", "transcription-error"):
            session["status"] = "ended"
            session["partial"] = ""
            if len(session["lines"]) > session["analyzed_upto"]:
                _start_analysis(code, session)
    return {"ok": True}


# ---- Scam analysis (Claude) ----------------------------------------------------------------

SCAM_PROMPT = """You check live phone-call transcripts for scams and social engineering.
Both voices are mixed into one transcript and lines are not labelled by speaker.
Reply with ONLY a JSON object, no other text:
{"is_scam": true/false, "risk": <integer 0-100>, "signs": ["short warning sign", ...], "summary": "<one short sentence>"}
Warning signs to look for include: gift card or crypto payment, wire transfers, deportation or arrest
threats, one-time code or password requests, remote access apps, upfront fees for jobs, LMIAs and work
permits, impersonating government/bank/police/IT/CRA/IRCC, urgency, secrecy, threats.
Use risk under 30 for normal conversation, 30-69 for suspicious, 70+ for likely scam."""

# Used when no ANTHROPIC_API_KEY is set, so the demo still works offline.
KEYWORD_SIGNS = {
    "Gift card or crypto payment": r"gift ?cards?|itunes|google play card|bitcoin|crypto|btc|usdt",
    "Wire / money transfer": r"wire (?:the )?money|western union|moneygram|e-?transfer|send (?:the )?money",
    "Arrest or deportation threat": r"arrest|warrant|deport|police will|jail|immigration (?:status|officer)",
    "One-time code request": r"(?:verification|one[- ]time|security|6[- ]digit) code|read (?:me )?the code|otp",
    "Password / login request": r"password|pin number|login details|sign[- ]in details",
    "Remote access request": r"anydesk|teamviewer|remote access|install (?:this|an) app|screen ?share",
    "Upfront fee for job / LMIA / work permit": r"lmia|work permit|processing fee|registration fee|pay (?:a |the )?fee",
    "Government / bank impersonation": r"\bcra\b|revenue agency|ircc|service canada|social insurance|\bsin\b|your bank|fraud department",
    "Urgency or secrecy": r"right now|immediately|urgent|don'?t tell|keep this (?:between|secret)|within (?:the next )?\d+ (?:minutes|hours)",
}


def _empty_risk() -> dict:
    return {"score": 0, "is_scam": False, "signs": [], "summary": "", "analyzed_lines": 0,
            "source": "none"}


def _start_analysis(code: str, session: dict) -> None:
    """Called with _lock held. Runs at most one analysis per session at a time."""
    if session["analyzing"]:
        return
    session["analyzing"] = True
    transcript = "\n".join(session["lines"])
    upto = len(session["lines"])
    threading.Thread(target=_analyze, args=(code, transcript, upto), daemon=True).start()


def _analyze(code: str, transcript: str, upto: int) -> None:
    try:
        result = _ask_claude(transcript) if ANTHROPIC_API_KEY else None
    except Exception as e:  # network / API error: fall back so the app still gets an answer
        print(f"[soteria] Claude analysis failed: {e}")
        result = None
    if result is None:
        result = _keyword_analysis(transcript)
    result["analyzed_lines"] = upto
    with _lock:
        session = _sessions.get(code)
        if session is None:
            return
        session["risk"] = result
        session["analyzing"] = False
        session["analyzed_upto"] = upto
        # More lines may have arrived while we were waiting on the AI.
        if session["status"] != "listening" and len(session["lines"]) > upto:
            _start_analysis(code, session)
        elif len(session["lines"]) - upto >= ANALYZE_EVERY_LINES:
            _start_analysis(code, session)


def _ask_claude(transcript: str) -> dict:
    import anthropic

    client = anthropic.Anthropic(api_key=ANTHROPIC_API_KEY)
    msg = client.messages.create(
        model=ANTHROPIC_MODEL,
        max_tokens=400,
        system=SCAM_PROMPT,
        messages=[{"role": "user", "content": "Transcript so far:\n" + transcript}],
    )
    raw = "".join(getattr(block, "text", "") for block in msg.content)
    start, end = raw.find("{"), raw.rfind("}")
    data = json.loads(raw[start:end + 1])
    score = max(0, min(100, int(data.get("risk", 0))))
    return {
        "score": score,
        "is_scam": bool(data.get("is_scam", score >= 70)),
        "signs": [str(x) for x in data.get("signs", [])][:6],
        "summary": str(data.get("summary", "")),
        "source": "claude",
    }


def _keyword_analysis(transcript: str) -> dict:
    text = transcript.lower()
    signs = [name for name, pattern in KEYWORD_SIGNS.items() if re.search(pattern, text)]
    score = min(95, 35 * len(signs))
    return {
        "score": score,
        "is_scam": score >= 70,
        "signs": signs,
        "summary": ("Possible scam: " + ", ".join(signs).lower()) if signs else "No warning signs yet.",
        "source": "keywords",
    }
