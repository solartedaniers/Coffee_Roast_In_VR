import httpx
import pytest
from pydantic import SecretStr

from app.config import SERVICE_ROOT
from app.net.bounded_http import BoundedHttpClient
from app.voice.deadline import Deadline
from app.voice.errors import VoiceErrorCode, VoiceRequestError
from app.voice.silence import SilencePhraseFilter
from app.voice.transcription.azure import AzureTranscriber, AzureTranscriptionOptions
from app.voice.transcription.base import NoSpeechDetected, TranscriptionUnavailable
from app.voice.transcription.chain import TranscriptionChain
from tests.voice.fakes import FakeClock, FakeTranscriber

AZURE_URL = "https://canadacentral.stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1"
KEY = "azure-test-key"
SILENCE = SilencePhraseFilter.from_file(SERVICE_ROOT / "config" / "whisper_silence_phrases.json")


def azure_with(handler, api_key=KEY) -> AzureTranscriber:
    http = BoundedHttpClient([AZURE_URL], 100_000, transport=httpx.MockTransport(handler))
    return AzureTranscriber(http, AzureTranscriptionOptions(
        url=AZURE_URL, api_key=SecretStr(api_key) if api_key else None, language="es-CO", sample_rate_hz=16_000,
        connect_timeout_seconds=1, read_timeout_seconds=1, max_response_bytes=100_000))


def test_azure_sends_the_wav_with_its_format_and_language():
    seen = {}

    def handler(request):
        seen["url"] = str(request.url)
        seen["headers"] = request.headers
        seen["body"] = request.content
        return httpx.Response(200, json={"RecognitionStatus": "Success", "DisplayText": "¿Subo el gas?"})

    assert azure_with(handler).transcribe(b"RIFF-audio", 5) == "¿Subo el gas?"
    assert seen["url"] == AZURE_URL + "?language=es-CO&format=simple"
    assert seen["headers"]["ocp-apim-subscription-key"] == KEY
    assert seen["headers"]["content-type"] == "audio/wav; codecs=audio/pcm; samplerate=16000"
    assert seen["body"] == b"RIFF-audio"


@pytest.mark.parametrize("status", ["NoMatch", "InitialSilenceTimeout", "BabbleTimeout"])
def test_azure_reports_audio_without_speech(status):
    with pytest.raises(NoSpeechDetected):
        azure_with(lambda request: httpx.Response(200, json={"RecognitionStatus": status})).transcribe(b"RIFF", 5)


@pytest.mark.parametrize("response", [
    httpx.Response(401), httpx.Response(429), httpx.Response(200, json={"RecognitionStatus": "Error"}),
    httpx.Response(200, text="<html>"),
], ids=["401", "429", "error", "not-json"])
def test_azure_reports_being_unavailable(response):
    with pytest.raises(TranscriptionUnavailable):
        azure_with(lambda request: response).transcribe(b"RIFF", 5)


def test_azure_is_not_called_without_a_key():
    def handler(request):
        raise AssertionError("Azure should not be called")

    with pytest.raises(TranscriptionUnavailable, match="key"):
        azure_with(handler, api_key=None).transcribe(b"RIFF", 5)


def chain(*transcribers, max_chars=500) -> TranscriptionChain:
    return TranscriptionChain(list(transcribers), SILENCE, max_chars)


def test_uses_the_first_provider_and_normalizes_spaces():
    groq = FakeTranscriber("groq", result="  ¿Cuándo   saco el café? ")
    azure = FakeTranscriber("azure", result="otra")

    transcript = chain(groq, azure).transcribe(b"wav", Deadline(10))

    assert (transcript.text, transcript.provider) == ("¿Cuándo saco el café?", "groq")
    assert azure.calls == 0


def test_falls_back_when_the_first_provider_fails():
    azure = FakeTranscriber("azure", result="¿Subo el gas?")

    transcript = chain(FakeTranscriber("groq", error=TranscriptionUnavailable("429")), azure).transcribe(
        b"wav", Deadline(10))

    assert (transcript.text, transcript.provider) == ("¿Subo el gas?", "azure")


@pytest.mark.parametrize("first", [
    FakeTranscriber("groq", result="Gracias por ver el video."), FakeTranscriber("groq", error=NoSpeechDetected()),
    FakeTranscriber("groq", result="   "),
], ids=["whisper-phrase", "no-speech", "empty"])
def test_silence_stops_without_spending_the_fallback(first):
    azure = FakeTranscriber("azure", result="no debería usarse")

    with pytest.raises(VoiceRequestError) as error:
        chain(first, azure).transcribe(b"wav", Deadline(10))

    assert error.value.code is VoiceErrorCode.SILENT_AUDIO
    assert azure.calls == 0


def test_fails_when_every_provider_fails():
    with pytest.raises(TranscriptionUnavailable):
        chain(FakeTranscriber("groq", error=TranscriptionUnavailable("x")),
              FakeTranscriber("azure", error=TranscriptionUnavailable("y"))).transcribe(b"wav", Deadline(10))


def test_does_not_start_another_provider_after_the_deadline():
    clock = FakeClock()
    azure = FakeTranscriber("azure", result="tarde")
    slow = FakeTranscriber("groq", error=TranscriptionUnavailable("lento"), clock=clock, takes=11)

    with pytest.raises(TranscriptionUnavailable):
        chain(slow, azure).transcribe(b"wav", Deadline(10, clock))

    assert azure.calls == 0


def test_cuts_very_long_transcripts():
    transcript = chain(FakeTranscriber("groq", result="a" * 50), max_chars=10).transcribe(b"wav", Deadline(10))

    assert transcript.text == "a" * 10
