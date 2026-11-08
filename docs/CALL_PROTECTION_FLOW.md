# Soteria – Call Protection Flow

How Soteria protects a phone call end-to-end: the app automatically adds a silent
Twilio "listener" to the call, Twilio transcribes it, an AI checks it for scams,
and the employee sees live warnings on the call screen.

> Legend: ✅ built · 🟡 built but not yet tested on a real phone · ⬜ to build

---

## 1. The big picture

```mermaid
flowchart LR
    Caller(["📞 Caller"])
    subgraph Phone["Employee's Android phone"]
        App["Soteria app<br/>(default phone app)"]
    end
    Carrier["Mobile carrier<br/>(3-way call)"]
    Twilio["Twilio number<br/>silent listener +<br/>real-time transcription"]
    Backend["Soteria backend<br/>(FastAPI)"]
    AI["AI model<br/>(Claude)"]

    Caller <--> Carrier
    App <--> Carrier
    Carrier <--> Twilio
    Twilio -- "transcript text" --> Backend
    Backend -- "transcript" --> AI
    AI -- "risk result" --> Backend
    App -- "polls transcript + risk" --> Backend
```

**In plain words**

1. A call connects on the employee's phone. Soteria is the default phone app, so it controls the call.
2. Soteria automatically dials our Twilio number and merges it into the call (a 3-way call).
3. Twilio stays silent, listens, and turns the speech into text.
4. Twilio sends the text to our backend, sentence by sentence.
5. The backend asks the AI: *"Is this a scam? How risky? Which warning signs?"*
6. The app shows the live transcript and any warning on the call screen.
7. After the call, the result is saved to **Call logs** and **Alerts**.

---

## 2. Step-by-step: one protected call

```mermaid
sequenceDiagram
    autonumber
    actor X as Caller
    participant C as Carrier
    participant T as Android Telecom
    participant A as Soteria app
    participant TW as Twilio number
    participant B as Backend
    participant AI as AI (Claude)

    X->>C: Calls the employee
    C->>T: Incoming call
    T->>A: Call added (ringing) - show incoming call screen
    A->>T: Employee taps Answer - call ACTIVE

    rect rgb(235, 242, 255)
    Note over A,B: Add the silent listener (automatic, about 2-5 s, caller hears hold)
    A->>B: POST /sessions
    B-->>A: One-time code, e.g. 483920
    A->>T: Dial Twilio number on same SIM (caller is put on hold)
    T->>C: Second call
    C->>TW: Call reaches Twilio
    TW->>B: POST /twilio/voice
    B-->>TW: Wait for 6 digits (Gather)
    A->>TW: Send code as keypad tones (caller is on hold, cannot hear)
    TW->>B: POST /twilio/joined with the digits
    B-->>TW: Start real-time transcription, stay on the line silently
    A->>B: GET /sessions/{code} until status = listening
    A->>T: Merge both calls (3-way conference)
    Note over X,TW: Caller + employee + silent Twilio listener
    end

    loop While the call is going
        TW->>B: POST /twilio/transcription (partial and final sentences)
        B->>AI: Transcript so far - is this a scam? (TO BUILD)
        AI-->>B: risk level, score, warning signs (TO BUILD)
        A->>B: GET /sessions/{code}?after=n (every 1.5 s)
        B-->>A: New sentences + latest risk
        A->>A: Show transcript and warning on call screen
    end

    X->>C: Hangs up
    T->>A: Call disconnected - app drops the Twilio leg
    TW->>B: transcription-stopped
    B->>AI: Final summary of the whole call (TO BUILD)
    A->>B: Fetch final result (TO BUILD)
    A->>A: Save to Call logs and Alerts (TO BUILD)
```

---

## 3. What happens when something goes wrong

Protection must **never break the call**. Any failure falls back to a normal call.

```mermaid
flowchart TD
    Start(["Call becomes ACTIVE"]) --> Check{"Protection on?<br/>Soteria is default phone app?<br/>Configured? Not an emergency call?<br/>Phone permission granted?"}
    Check -- No --> Normal(["Normal call, no protection"])
    Check -- Yes --> Session["Ask backend for session code"]
    Session -- "server unreachable" --> Fail
    Session --> Dial["Dial Twilio number<br/>(caller on hold)"]
    Dial -- "no answer / dial blocked" --> Fail
    Dial --> Code["Send code as keypad tones"]
    Code --> Listen{"Backend confirms<br/>'listening'?"}
    Listen -- "not within 30 s" --> Fail
    Listen -- Yes --> Merge{"Carrier can<br/>merge calls?"}
    Merge -- No --> Fail
    Merge -- Yes --> Protected(["✅ Protected · Transcribing"])
    Fail["❌ Fallback:<br/>hang up Twilio leg,<br/>take caller off hold,<br/>show reason on call screen"] --> Normal2(["Call continues unprotected"])
```

Emergency calls (911 etc.) are **never** held or merged.

---

## 4. What's built vs. what's left

### Android app (`app/`)

| Feature | Status | Where |
|---|---|---|
| Home screen, Activate button, overview, latest alert | ✅ | `HomeActivity.java` |
| Become default phone app + permissions on Activate | ✅ | `HomeActivity.java`, `ProtectionSettings.java` |
| Keypad (required for default phone app) | ✅ | `DialerActivity.java` |
| Incoming / in-call screen (answer, decline, mute, speaker, hang up) | ✅ | `InCallActivity.java` |
| Receive calls from Android | ✅ | `SoteriaInCallService.java` |
| Auto-dial Twilio, send code, merge, fallback on failure | 🟡 | `CallManager.java` |
| Live transcript on call screen | 🟡 | `InCallActivity.java`, `SoteriaApi.java` |
| Call logs screen | ✅ (demo data) | `CallLogsActivity.java`, `CallLogRepository.java` |
| Alerts screen (review / false alarm / undo) | ✅ (demo data) | `AlertsActivity.java`, `AlertStore.java` |
| Show live **risk warning** on call screen | ⬜ | `InCallActivity.java` |
| Save real calls to Logs/Alerts (replace demo data, e.g. Room DB) | ⬜ | `CallLogRepository.java` |
| Settings screen | ⬜ | `SettingsActivity.java` |
| "Setup needed" state when Twilio isn't configured | ⬜ | `HomeActivity.java` |

### Backend (`backend/`)

| Feature | Status | Where |
|---|---|---|
| Create session / get transcript (`/sessions`) | ✅ | `main.py` |
| Twilio webhooks: answer, check code, start transcription | ✅ | `main.py` |
| Receive transcription events | ✅ | `main.py` |
| Twilio signature check + app API key | ✅ | `main.py` |
| Flow test (no Twilio needed) | ✅ | `test_flow.py` |
| **AI risk analysis** during the call | ⬜ | new |
| AI summary when the call ends | ⬜ | new |
| Database (sessions are in memory today) | ⬜ | new |
| Deploy to a real HTTPS server (today: local + ngrok) | ⬜ | — |
| Per-company / per-user accounts | ⬜ | — |

---

## 5. The AI step (to build)

**Where:** backend, not the app (keeps the AI key secret, works even if the phone app is closed).

**When:** after every few *final* transcript sentences (e.g. every 2–3 sentences or ~10 s),
send the transcript so far to the AI. Run it in the background so Twilio's webhook
returns immediately. Do a last pass when the call ends for a summary.

**What the AI returns** (matches the fields the app already uses in `CallLog.java`):

```json
{
  "risk": "SAFE | MONITORING | FLAGGED",
  "score": 0.96,
  "signals": ["Possible impersonation", "Artificial urgency", "Credential request"],
  "explanation": "Caller claims to be IT and asks for the admin password.",
  "advice": "Don't share credentials. Hang up and call IT back on a known number."
}
```

**API change:** `GET /sessions/{code}` also returns the latest `risk` object, so the app gets
transcript + risk in the same poll it already does.

**Model:** a fast, cheap model (e.g. Claude Haiku 5.5) for the live checks; a stronger model
(e.g. Claude Sonnet 5.5) for the end-of-call summary.

---

## 6. Setup checklist

### Twilio
- [ ] Buy a voice-capable phone number
- [ ] Set "A call comes in" webhook → `https://<backend>/twilio/voice` (POST)
- [ ] Confirm real-time transcription is enabled on the account

### Backend environment variables
| Variable | Purpose |
|---|---|
| `PUBLIC_BASE_URL` | Public HTTPS URL of the backend (e.g. ngrok) |
| `TWILIO_AUTH_TOKEN` | Verifies requests really come from Twilio |
| `SOTERIA_API_KEY` | Shared secret between app and backend |
| `ANTHROPIC_API_KEY` | ⬜ for the AI step |

### Android `local.properties` (not committed)
```
soteria.listenerNumber=+1XXXXXXXXXX
soteria.backendUrl=https://<backend>
soteria.apiKey=<same as SOTERIA_API_KEY>
```

### Testing
- Emulator: UI, incoming/outgoing call screens (`adb emu gsm call 5551234`)
- **Real phone with SIM required** for the merge + transcription (emulators can't do real 3-way calls)

---

## 7. Open questions for the team

1. **Consent:** play "this call may be recorded" after the merge? (Required in many places.)
2. **Speaker labels:** both voices arrive mixed — do we need "Caller:" / "Employee:" labels?
3. **Data retention:** how long do we keep transcripts, and where?
4. **Company dashboard:** do admins need to see alerts across all employees?
5. **Which calls to protect:** all calls, external numbers only, or skip saved contacts?
6. **Carriers:** which carriers/plans do our customers use (3-way calling support)?
