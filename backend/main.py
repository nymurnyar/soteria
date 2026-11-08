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
7. The app polls ``GET /sessions/{code}`` for new transcript lines.

Sessions are kept in memory -- fine for a prototype, use Redis/DB in production.
"""

import json
import os
import secrets
import threading
import time
from typing import Dict, Optional
from xml.sax.saxutils import quoteattr

from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.responses import Response
from twilio.request_validator import RequestValidator

PUBLIC_BASE_URL = os.environ.get("PUBLIC_BASE_URL", "").rstrip("/")  # e.g. https://abc.ngrok.app
TWILIO_AUTH_TOKEN = os.environ.get("TWILIO_AUTH_TOKEN", "")
SOTERIA_API_KEY = os.environ.get("SOTERIA_API_KEY", "")
VALIDATE_TWILIO = os.environ.get("VALIDATE_TWILIO", "1") != "0"
LANGUAGE_CODE = os.environ.get("TRANSCRIPTION_LANGUAGE", "en-US")

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
                session["partial"] = ""
            else:
                session["partial"] = text
        elif event in ("transcription-stopped", "transcription-error"):
            session["status"] = "ended"
            session["partial"] = ""
    return {"ok": True}
