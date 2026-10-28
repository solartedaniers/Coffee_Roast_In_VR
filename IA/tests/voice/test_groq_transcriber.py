import httpx
import pytest
from pydantic import SecretStr

from app.net.bounded_http import BoundedHttpClient
from app.voice.transcription.base import TranscriptionUnavailable
from app.voice.transcription.groq import GroqTranscriber, GroqTranscriptionOptions

BASE_URL = "https://api.groq.com/openai/v1"
KEY = "gsk_test_key"


def transcriber_with(handler, api_key=KEY) -> GroqTranscriber:
    http = BoundedHttpClient([BASE_URL], 100_000, transport=httpx.MockTransport(handler))
    return GroqTranscriber(http, GroqTranscriptionOptions(
        base_url=BASE_URL, api_key=SecretStr(api_key) if api_key else None, model="whisper-large-v3-turbo",
        language="es", prompt="tueste de café", connect_timeout_seconds=1, read_timeout_seconds=1))


def test_sends_the_wav_in_spanish_with_temperature_zero():
    seen = {}

    def handler(request):
        seen["url"] = str(request.url)
        seen["auth"] = request.headers["authorization"]
        seen["body"] = request.content
        return httpx.Response(200, json={"text": " ¿Cuándo saco el café? "})

    text = transcriber_with(handler).transcribe(b"RIFF-audio", 5)

    assert text == " ¿Cuándo saco el café? "
    assert seen["url"] == "https://api.groq.com/openai/v1/audio/transcriptions"
    assert seen["auth"] == f"Bearer {KEY}"
    for part in (b"whisper-large-v3-turbo", b'name="language"', b"es", b'name="temperature"', b"RIFF-audio"):
        assert part in seen["body"]


def test_does_not_call_groq_without_a_key():
    def handler(request):
        raise AssertionError("Groq should not be called")

    with pytest.raises(TranscriptionUnavailable, match="key"):
        transcriber_with(handler, api_key=None).transcribe(b"RIFF", 5)


@pytest.mark.parametrize("response", [
    httpx.Response(429, json={"error": "rate limit"}), httpx.Response(500), httpx.Response(200, json={"x": 1}),
    httpx.Response(200, json={"text": None}), httpx.Response(200, text="no json"),
], ids=["429", "500", "no-text", "null-text", "not-json"])
def test_reports_groq_being_unavailable(response):
    with pytest.raises(TranscriptionUnavailable):
        transcriber_with(lambda request: response).transcribe(b"RIFF", 5)


def test_never_puts_the_key_in_the_error():
    with pytest.raises(TranscriptionUnavailable) as error:
        transcriber_with(lambda request: httpx.Response(401, text=KEY)).transcribe(b"RIFF", 5)

    assert KEY not in str(error.value)
