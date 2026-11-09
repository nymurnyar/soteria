# SOTERIA 🛡️

### Don't take the bait.

**AI-Powered Real-Time Protection Against Voice Phishing (Vishing)**

Soteria is an AI-powered cybersecurity solution designed to protect businesses and their employees from voice phishing attacks. By analyzing phone conversations in real time, Soteria identifies suspicious language, impersonation attempts and social-engineering tactics before employees disclose sensitive information or compromise organizational security.

Built for **natHacks 2026**.

---

## 1. The Problem

Voice phishing (vishing) is an increasingly serious cybersecurity threat. Attackers impersonate trusted organizations, executives, banks and technical support personnel to manipulate employees into revealing credentials, transferring money or granting unauthorized access.

Traditional security solutions primarily focus on identifying suspicious phone numbers. However, they often fail to recognize malicious intent within an ongoing conversation.

**The problem isn't always who is calling. It's what they're trying to make you do.**

## 2. The Solution

Soteria provides an additional layer of organizational security by monitoring phone conversations and identifying potential social-engineering attacks as they happen.

Unlike conventional spam detection, Soteria analyzes the actual content and context of conversations to identify suspicious requests, manipulation tactics and impersonation attempts.

Our goal is to help organizations protect employees before a conversation turns into a security incident.

## 3. How Soteria Works

Soteria uses a four-stage detection pipeline:

| **Stage** | **Description** |
|---|---|
| **01. Transcribe** | Converts call audio into text for analysis through our speech-to-text backend. |
| **02. Analyse** | Google Gemini examines conversations for impersonation, suspicious requests and manipulation tactics. |
| **03. Assess** | Evaluates conversational context and assigns a risk score from 0–100. |
| **04. Alert** | Notifies users when potential threats are detected, highlighting warning signs. |

### Detection Architecture

```text
       Phone Conversation
               |
               v
         Audio Capture
               |
               v
       Speech-to-Text
               |
               v
       Generated Transcript
               |
          +----+----+
          |         |
          v         v
      Keyword    Gemini AI
      Detection  Analysis
          |         |
          +----+----+
               |
               v
         Risk Assessment
               |
               v
         Alert Threshold
               |
               v
        Real-Time Warning
```

### Our Differentiator

**Traditional security identifies suspicious callers. Soteria identifies suspicious conversations.**

Rather than relying exclusively on known spam numbers, Soteria transcribes onsite using the phone's built-in speech-to-text, then examines what callers say and how they attempt to manipulate employees.

---

## 4. Key Features

- **Real-Time Conversation Analysis:** Evaluates phone conversations for potential vishing attacks.
- **AI-Powered Detection:** Uses Google Gemini to identify contextual social-engineering patterns.
- **Dual-Layer Detection:** Combines immediate keyword checks with contextual AI analysis.
- **Risk Scoring:** Assigns a score between 0 and 100 to communicate potential threat severity.
- **Configurable Sensitivity:** Allows users to select their preferred detection threshold.
- **Real-Time Alerts:** Provides visual warnings, vibrations and notifications.
- **Call History:** Maintains accessible records of calls, transcripts and detection results.

## 5. Technology Stack

| **Component** | **Technology** |
|---|---|
| Android Application | Java |
| UI Framework | XML, Material 3 |
| Calling Framework | Android Telecom |
| Backend | Python, FastAPI |
| Speech-to-Text | Backend speech recognition |
| AI Analysis | Google Gemini API |
| Detection Engine | Rule-Based Keyword Detection |

## 6. Getting Started

Follow these instructions to access and run Soteria locally.

### Prerequisites

Before getting started, ensure you have:

- [Android Studio](https://developer.android.com/studio)
- [Python 3](https://www.python.org/downloads/)
- A compatible Android device or emulator
- A Google Gemini API key

### 1. Clone the Repository

Open your terminal and run:

```bash
git clone <REPOSITORY_URL>
cd <REPOSITORY_NAME>
```

### 2. Configure the Backend

Navigate to the backend directory:

```bash
cd backend
```

Create a Python virtual environment:

```bash
python -m venv venv
```

Activate it on macOS/Linux:

```bash
source venv/bin/activate
```

Install the required dependencies:

```bash
pip install -r requirements.txt
```

Create a `.env` file in the backend directory and add your Gemini API key:

```env
GEMINI_API_KEY=your_gemini_api_key
```

Start the FastAPI server:

```bash
uvicorn main:app --reload
```

Once running, access the API documentation at:

`http://127.0.0.1:8000/docs`

### 3. Run the Android Application

1. Open Android Studio.
2. Select **Open** and navigate to the cloned Soteria repository.
3. Allow Gradle synchronization to complete.
4. Configure the application's backend API endpoint.
5. Ensure your Android device or emulator can communicate with the backend.
6. Select your target Android device or emulator.
7. Click **Run** to build and launch Soteria.
8. Grant the required permissions and complete the consent process.

**Note:** When using the standard Android emulator, use `10.0.2.2` instead of `localhost` to access a backend running on your computer.

### Important

- Keep your Gemini API key private. Never commit your `.env` file or expose API credentials in the Android application.
- Ensure the backend is running before testing features that require server communication.
- Call-audio capture and transcription capabilities may vary depending on Android version and device compatibility.

---

## 7. Future Roadmap

Our roadmap focuses on three stages as we move from a working prototype toward broader enterprise protection.

### 01. BUILD

- Complete Android and Gemini integration.
- Implement real-time risk assessments and employee alerts.
- Validate Android call-transcription capabilities.

### 02. VALIDATE

- Partner with 1–2 businesses for pilot testing.
- Test with 20–30 employees.
- Evaluate detection accuracy, false positives and alert speed.

### 03. SCALE

- Expand deployment to more organizations.
- Introduce enterprise security dashboards.
- Explore partnerships with banks, telecom providers and cybersecurity firms.

**Our Vision:** Make Soteria a trusted layer of protection against social engineering in the workplace.

*Note: Pilot numbers are proposed targets, not confirmed partnerships or completed tests.*

## 8. Supporting Materials

- **Slides:** [Soteria Pitch Deck](https://nymurnyar.github.io/soteria/soteria-pitch.html#1)

## 9. Credits

**Built by Team Soteria for natHacks 2026.**

### Team Members

- Nymur Nyar (Team Leader)
- Ercel Angeles
- Pearl Haidar
- Andrew Oke
- Yatri Maheshbhai Surani
- Avni Kataria

### Technologies and Resources

- [Android Developers](https://developer.android.com/)
- [Google Gemini API](https://ai.google.dev/gemini-api/docs)
- [FastAPI](https://fastapi.tiangolo.com/)
- [Material Design 3](https://m3.material.io/)
- [Claude AI](https://claude.ai/)

---

**SOTERIA — Don't take the bait. 🛡️**
