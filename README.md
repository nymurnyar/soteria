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

| **Stage**          | **Description**                                                                                       |
| ------------------ | ----------------------------------------------------------------------------------------------------- |
| **01. Transcribe** | Converts call audio into text for analysis through our speech-to-text backend.                        |
| **02. Analyse**    | Google Gemini examines conversations for impersonation, suspicious requests and manipulation tactics. |
| **03. Assess**     | Evaluates conversational context and assigns a risk score from 0–100.                                 |
| **04. Alert**      | Notifies users when potential threats are detected, highlighting warning signs.                       |

### Detection Architecture
```
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
          |         |
          v         v
      Keyword    Gemini AI
      Detection  Analysis
          |         |
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

Rather than relying exclusively on known spam numbers, Soteria examines what callers say and how they attempt to manipulate employees.



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

| **Component**       | **Technology**               |
| ------------------- | ---------------------------- |
| Android Application | Java                         |
| UI Framework        | XML, Material 3              |
| Calling Framework   | Android Telecom              |
| Backend             | Python, FastAPI              |
| Speech-to-Text      | Backend speech recognition   |
| AI Analysis         | Google Gemini API            |
| Detection Engine    | Rule-Based Keyword Detection |

## 6. Supporting Materials

- **Slides:** https://nymurnyar.github.io/soteria/soteria-pitch.html#1

## 7. Credits

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
