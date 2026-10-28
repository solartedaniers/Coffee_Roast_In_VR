import base64
import json

import pytest
from fastapi.testclient import TestClient

from app.api import create_app
from app.config import SERVICE_ROOT
from app.container import Services, build_services
from app.feedback.cache import FeedbackCache
from app.rag.citations import Citation
from app.voice.contracts import AnswerStatus
from app.voice.errors import VoiceErrorCode, VoiceRequestError
from app.voice.messages import VoiceMessages
from app.voice.service import VoiceAnswer
from app.voice.speech.base import SynthesizedAudio
from tests.voice.wav_factory import tone_wav

MESSAGES = VoiceMessages.from_file(SERVICE_ROOT / "config" / "voice_messages.json")
CONTEXT = {"userId": 7, "knowledgeLevel": "INTERMEDIATE", "roastAttemptId": "8a7c6f0e-2a55-4c1e-9d43-0b6f1f3d2e10",
           "roastState": {"elapsedSeconds": 300, "beanTemperature": 190.5, "firstCrackReached": False}}
REPLY_WAV = tone_wav(0.2, rate=22_050)
ANSWER = VoiceAnswer(AnswerStatus.ANSWERED, "¿Cuándo saco el café?", "Cuando llegue al color objetivo.",
                     [Citation("manual.pdf", 4, "Manual")], SynthesizedAudio(REPLY_WAV, 22_050, "azure"),
                     "groq", "groq", {"transcription": 400, "retrieval": 300, "answer": 900, "speech": 700}, 2350)


class FakeVoice:
    def __init__(self, answer=ANSWER, error=None):
        self.answer = answer
        self.error = error
        self.calls = []

    def ask(self, context, wav):
        self.calls.append((context, wav))
        if self.error:
            raise self.error
        return self.answer

    def warm_up(self, seconds):
        pass


def client_for(voice, max_audio_bytes=600_000) -> TestClient:
    services = Services(None, None, FeedbackCache(60, 10), None, voice=voice, voice_messages=MESSAGES,
                        voice_max_audio_bytes=max_audio_bytes)
    return TestClient(create_app(services, ensure_schema=False))


def ask(client, context=CONTEXT, wav=None):
    return client.post("/api/v1/voice/questions",
                       files={"audio": ("question.wav", wav or tone_wav(1.0), "audio/wav")},
                       data={"context": json.dumps(context) if isinstance(context, dict) else context})


def test_answers_with_text_citations_and_base64_audio():
    voice = FakeVoice()

    response = ask(client_for(voice))

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ANSWERED"
    assert body["question"] == "¿Cuándo saco el café?"
    assert body["answer"] == "Cuando llegue al color objetivo."
    assert body["citations"] == [{"fileName": "manual.pdf", "pageNumber": 4, "title": "Manual"}]
    assert body["audio"]["format"] == "wav" and body["audio"]["sampleRateHz"] == 22_050
    assert base64.b64decode(body["audio"]["base64"]) == REPLY_WAV
    assert body["providers"] == {"transcription": "groq", "answer": "groq", "speech": "azure"}
    assert body["latency"] == {"transcriptionMillis": 400, "retrievalMillis": 300, "answerMillis": 900,
                               "speechMillis": 700, "totalMillis": 2350}
    context, wav = voice.calls[0]
    assert context.user_id == 7 and context.roast_state.bean_temperature == 190.5
    assert wav == tone_wav(1.0)


def test_answers_text_without_audio_when_no_voice_worked():
    answer = VoiceAnswer(AnswerStatus.UNAVAILABLE, None, MESSAGES.unavailable, [], None, None, None, {}, 10)

    body = ask(client_for(FakeVoice(answer))).json()

    assert (body["status"], body["audio"], body["answer"]) == ("UNAVAILABLE", None, MESSAGES.unavailable)


@pytest.mark.parametrize(("code", "status"), [
    (VoiceErrorCode.SILENT_AUDIO, 422), (VoiceErrorCode.ASSISTANT_BUSY, 409), (VoiceErrorCode.RATE_LIMITED, 429),
    (VoiceErrorCode.AUDIO_TOO_LARGE, 413), (VoiceErrorCode.AUDIO_INVALID, 400),
])
def test_reports_request_errors_with_a_stable_code_and_message(code, status):
    response = ask(client_for(FakeVoice(error=VoiceRequestError(code))))

    assert response.status_code == status
    assert response.json() == {"code": code.value, "message": MESSAGES.error(code)}


@pytest.mark.parametrize("context", [
    "{not json", CONTEXT | {"userId": None}, CONTEXT | {"roastState": {"elapsedSeconds": -5}},
], ids=["not-json", "no-user", "bad-state"])
def test_rejects_an_invalid_context_without_asking(context):
    voice = FakeVoice()

    response = ask(client_for(voice), context=context)

    assert (response.status_code, response.json()["code"]) == (400, "CONTEXT_INVALID")
    assert voice.calls == []


def test_reads_at_most_one_byte_over_the_audio_limit():
    voice = FakeVoice()

    ask(client_for(voice, max_audio_bytes=100), wav=b"x" * 5000)

    assert len(voice.calls[0][1]) == 101


def test_rejects_a_request_without_audio():
    response = client_for(FakeVoice()).post("/api/v1/voice/questions", data={"context": json.dumps(CONTEXT)})

    assert response.status_code == 400


def test_keeps_the_existing_endpoints_when_voice_is_not_configured():
    services = Services(None, None, FeedbackCache(60, 10), None)
    client = TestClient(create_app(services, ensure_schema=False))

    assert ask(client).status_code == 404


def test_builds_the_real_voice_assistant_without_contacting_anything(settings):
    services = build_services(settings)

    pipeline = services.voice.pipeline
    assert [t.name for t in pipeline.transcription.transcribers] == ["groq", "azure"]
    assert [s.name for s in pipeline.speech.synthesizers] == ["azure", "piper"]
    assert pipeline.answerer.options.model == "openai/gpt-oss-20b"
    assert services.voice_max_audio_bytes == 600_000
