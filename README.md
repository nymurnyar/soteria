# Soteria

**Real-time scam and social-engineering protection for phone calls.**

Soteria is an Android phone app that quietly adds a listener line (**TrustLine**) to your calls, transcribes the conversation live, and has an AI score the scam risk every few sentences. If the call looks risky, the call screen shows a warning while you're still on the line, and the call gets saved to Logs and Alerts.

Built for **natHacks 2026, Standard Division, Defense & Security theme**.

---

## How it works

```
📞 Call connects (Soteria is the default phone app)
        ↓
🔗 App auto-dials the TrustLine (Twilio) number and merges it into the call
        ↓
🎙️ Twilio transcribes the call in real time
        ↓
🖥️ Backend (main.py) receives each sentence
        ↓
🤖 Every 2 sentences: gpt-oss scores the call
   "Is this a scam? How risky (0–100)? Which warning signs?"
        ↓
🖥️ Backend saves the result on the call session
        ↓
📱 App polls the backend every 1.5 s for transcript + risk
        ↓
⚠️ Warning banner + vibration on the call screen, call saved to Logs and Alerts
```

Full technical breakdown: [ARCHITECTURE.md](ARCHITECTURE.md).

## Features

| Feature | Status |
|---|---|
| Default phone app (dialer, incoming/in-call screens, notifications) | ✅ |
| Auto-add TrustLine number and merge into the call | ✅ code done, needs our own Twilio number to test |
| Live transcription via Twilio | ✅ code done, needs our own Twilio number to test |
| AI scam scoring with gpt-oss (keyword fallback if the model is offline) | ✅ tested with simulated transcripts |
| In-call risk banner (amber 30–69, red 70+) + vibration | ✅ |
| Logs and Alerts pages (with open/resolved status) | ✅ |
| Settings (monitoring, session, alerts, trusted contacts, privacy & permissions) | ✅ |
| Floating overlay bubble for calls/messaging apps | 🟡 on branch `feature/overlay`, see `OVERLAY.md` |
| SMS / email / WhatsApp monitoring, notify trusted contacts | ⏳ settings exist, not wired up |
| Voice ID on video calls | ❌ out of scope for MVP |

## Tech stack

- **App:** Android, Java, `InCallService` (Telecom API), min SDK 24
- **Backend:** Python, FastAPI, Uvicorn
- **Telephony + speech-to-text:** Twilio Voice, `<Start><Transcription>`
- **AI:** gpt-oss-20b via LM Studio (any OpenAI-compatible server works: Ollama, Groq)
- **Tunnel:** ngrok (backend runs on a laptop for the demo)

## Repo layout

```
app/                     Android app (Java)
  src/main/java/com/example/soteria/
    CallManager.java       call state, TrustLine dial + merge, polling, risk
    SoteriaInCallService   hooks Soteria into Android's phone system
    SoteriaApi.java        HTTP client for the backend
    InCallActivity.java    call screen + live transcript + risk banner
    ...                    Home, Dialer, Logs, Alerts, Settings
backend/
  main.py                FastAPI server: sessions, Twilio webhooks, AI scoring
  test_flow.py           end-to-end test without Twilio
  start-backend.bat      Windows one-click start (backend + ngrok)
branding/                logo
```

## Running it

### 1. Backend (on a laptop)

1. **Start the AI:** open LM Studio → load `gpt-oss-20b` → Developer tab → **Start Server** (port 1234).
2. **Start the backend + tunnel:**
   - Windows: double-click `backend/start-backend.bat`
   - Manual:
     ```bash
     cd backend
     python -m venv .venv && source .venv/bin/activate
     pip install -r requirements.txt
     export SOTERIA_API_KEY="<any long random string>"
     export PUBLIC_BASE_URL="https://<your-ngrok-url>"
     uvicorn main:app --host 0.0.0.0 --port 8000
     ngrok http 8000      # separate terminal
     ```
3. Copy the `https://....ngrok-free.app` URL. It changes every time ngrok restarts.

Optional env vars: `LLM_BASE_URL`, `LLM_MODEL`, `LLM_API_KEY`, `ANALYZE_EVERY_LINES` (default 2), `VALIDATE_TWILIO=1` + `TWILIO_AUTH_TOKEN` for signature checks.

### 2. Twilio

1. Buy a voice-capable number.
2. Phone Numbers → your number → Voice configuration → "A call comes in": **Webhook**, `https://<ngrok-url>/twilio/voice`, **POST**.

### 3. App

1. Open the project in Android Studio and build (or install the APK).
2. Set Soteria as the **default phone app** when asked.
3. In **Settings**, fill in **TrustLine number** (the Twilio number) and **Backend URL (ngrok)**.
   Or set them at build time in `local.properties` (not committed):
   ```
   soteria.listenerNumber=+15551234567
   soteria.backendUrl=https://<your-ngrok-url>
   soteria.apiKey=<same as SOTERIA_API_KEY>
   ```

### Testing without Twilio

```bash
cd backend
python test_flow.py
```
Simulates a call session with a scam transcript and checks the risk result.

## Known limitations

- Backend, tunnel and AI all run on one laptop: it must stay on with LM Studio and both terminals open.
- Sessions live in memory; restarting the backend loses them.
- Both voices are mixed on one track, so transcript lines aren't labelled by speaker.
- Carrier must support 3-way calling for the merge.
- Recording/transcribing calls needs consent in many places. A production version should announce the listener and follow local law.


