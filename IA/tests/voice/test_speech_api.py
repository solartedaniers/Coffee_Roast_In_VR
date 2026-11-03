import base64

from fastapi.testclient import TestClient

from app.api import create_app
from app.config import SERVICE_ROOT
from app.container import Services
from app.feedback.cache import FeedbackCache
from app.voice.messages import VoiceMessages
from app.voice.speech.base import SynthesizedAudio
from app.voice.speech.text_speech import SpeechTextRejected
from tests.voice.wav_factory import tone_wav

MESSAGES = VoiceMessages.from_file(SERVICE_ROOT / "config" / "voice_messages.json")
WAV = tone_wav(0.2, rate=22_050)
SPEECH = "/api/v1/voice/speech"


class FakeSpeech:
    def __init__(self, audio=SynthesizedAudio(WAV, 22_050, "azure"), error=None):
        self.audio = audio
        self.error = error
        self.texts = []

    def speak(self, text):
        self.texts.append(text)
        if self.error:
            raise self.error
        return self.audio


def client_for(speech) -> TestClient:
    services = Services(None, None, FeedbackCache(60, 10), None, voice_messages=MESSAGES, speech=speech)
    return TestClient(create_app(services, ensure_schema=False))


def test_answers_the_spoken_text_as_base64_wav():
    speech = FakeSpeech()

    response = client_for(speech).post(SPEECH, json={"text": "Tu nota final es 78 %."})

    assert response.status_code == 200
    body = response.json()
    assert body["provider"] == "azure"
    assert body["audio"]["format"] == "wav"
    assert body["audio"]["sampleRateHz"] == 22_050
    assert base64.b64decode(body["audio"]["base64"]) == WAV
    assert speech.texts == ["Tu nota final es 78 %."]


def test_answers_503_with_its_code_when_no_voice_could_speak():
    response = client_for(FakeSpeech(audio=None)).post(SPEECH, json={"text": "Hola"})

    assert response.status_code == 503
    assert response.json() == {"code": "SPEECH_UNAVAILABLE", "message": MESSAGES.errors["SPEECH_UNAVAILABLE"]}


def test_answers_400_with_its_code_when_the_text_is_too_long():
    response = client_for(FakeSpeech(error=SpeechTextRejected("long"))).post(SPEECH, json={"text": "a" * 900})

    assert response.status_code == 400
    assert response.json()["code"] == "SPEECH_TEXT_INVALID"


def test_answers_400_without_text():
    speech = FakeSpeech()

    response = client_for(speech).post(SPEECH, json={})

    assert response.status_code == 400
    assert speech.texts == []


def test_the_route_does_not_exist_without_the_speech_service():
    response = client_for(None).post(SPEECH, json={"text": "Hola"})

    assert response.status_code == 404
