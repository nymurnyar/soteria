# Soteria listener backend

A small FastAPI server that lets a Twilio number join a phone call as a silent
third participant and transcribe it in real time.

## How a protected call works

1. A call connects on the employee's phone (Soteria is the default phone app).
2. The app asks this server for a one-time session code (`POST /sessions`).
3. The app dials your Twilio number; Android puts the original call on hold.
4. Twilio calls `POST /twilio/voice`; the app sends the 6-digit code as DTMF.
5. `POST /twilio/joined` checks the code and starts Twilio real-time transcription.
6. The app sees the session is `listening` and merges the two calls (3-way call).
7. Transcription events arrive at `POST /twilio/transcription`; the app polls
   `GET /sessions/{code}` and shows the transcript live.

The caller never hears the DTMF tones: the merge only happens after the code is accepted.

## Run locally

```bash
cd backend
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

export PUBLIC_BASE_URL="https://<your-ngrok-subdomain>.ngrok.app"
export TWILIO_AUTH_TOKEN="<twilio auth token>"
export SOTERIA_API_KEY="<any long random string>"   # must match the app

uvicorn main:app --host 0.0.0.0 --port 8000
ngrok http 8000          # in another terminal
```

For quick local testing without signature checks: `export VALIDATE_TWILIO=0`.

## Twilio setup

1. Buy a voice-capable phone number in the Twilio console.
2. Phone Numbers -> your number -> **Voice configuration**:
   - "A call comes in": **Webhook**, `https://<PUBLIC_BASE_URL>/twilio/voice`, HTTP **POST**.
3. Real-time transcription (`<Start><Transcription>`) is billed per minute by Twilio.

## Android app configuration

Add to the project's `local.properties` (not committed to git):

```
soteria.listenerNumber=+15551234567
soteria.backendUrl=https://<your-ngrok-subdomain>.ngrok.app
soteria.apiKey=<same value as SOTERIA_API_KEY>
```

## Notes / limitations

- Sessions are stored in memory; restart = lost transcripts. Use Redis/Postgres for production.
- Both voices arrive mixed on one track, so lines aren't labelled by speaker yet.
- Recording/transcribing calls requires consent in many places. Consider playing a short
  notice after the merge, and check local law and company policy.
