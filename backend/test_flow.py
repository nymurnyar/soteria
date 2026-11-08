"""End-to-end check of the session flow without Twilio. Run: .venv/bin/python -m pytest -q  (or just python test_flow.py)"""

import json
import os

os.environ["VALIDATE_TWILIO"] = "0"
os.environ["SOTERIA_API_KEY"] = "test-key"
os.environ["PUBLIC_BASE_URL"] = "https://example.test"

from fastapi.testclient import TestClient  # noqa: E402

import main  # noqa: E402

client = TestClient(main.app)
HEADERS = {"X-Soteria-Key": "test-key"}


def test_full_flow():
    # App needs the API key.
    assert client.post("/sessions").status_code == 401

    code = client.post("/sessions", headers=HEADERS).json()["code"]
    assert len(code) == 6
    assert client.get(f"/sessions/{code}", headers=HEADERS).json()["status"] == "waiting"

    # Twilio answers the listener leg and gathers the code.
    voice = client.post("/twilio/voice", data={"CallSid": "CA1"}).text
    assert "<Gather" in voice and 'numDigits="6"' in voice

    # Wrong code hangs up; right code starts transcription.
    assert "<Hangup/>" in client.post("/twilio/joined", data={"Digits": "000000x"}).text
    joined = client.post("/twilio/joined", data={"Digits": code, "CallSid": "CA1"}).text
    assert "<Transcription" in joined and f"code={code}" in joined
    assert client.get(f"/sessions/{code}", headers=HEADERS).json()["status"] == "listening"

    # Partial then final transcription events.
    url = f"/twilio/transcription?code={code}"
    client.post(url, data={"TranscriptionEvent": "transcription-content", "Final": "false",
                           "TranscriptionData": json.dumps({"transcript": "Hello this is"})})
    s = client.get(f"/sessions/{code}", headers=HEADERS).json()
    assert s["partial"] == "Hello this is" and s["lines"] == []

    client.post(url, data={"TranscriptionEvent": "transcription-content", "Final": "true",
                           "TranscriptionData": json.dumps({"transcript": "Hello this is IT support."})})
    s = client.get(f"/sessions/{code}", headers=HEADERS).json()
    assert s["lines"] == ["Hello this is IT support."] and s["partial"] == "" and s["next"] == 1

    # Incremental polling only returns new lines.
    assert client.get(f"/sessions/{code}?after=1", headers=HEADERS).json()["lines"] == []

    client.post(url, data={"TranscriptionEvent": "transcription-stopped"})
    assert client.get(f"/sessions/{code}", headers=HEADERS).json()["status"] == "ended"


if __name__ == "__main__":
    test_full_flow()
    print("OK")
