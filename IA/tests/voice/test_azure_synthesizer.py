import httpx
import pytest
from pydantic import SecretStr

from app.net.bounded_http import BoundedHttpClient
from app.voice.speech.azure import AzureSpeechOptions, AzureSynthesizer
from app.voice.speech.base import SynthesisUnavailable
from tests.voice.wav_factory import tone_wav

URL = "https://canadacentral.tts.speech.microsoft.com/cognitiveservices/v1"
KEY = "azure-test-key"


def synthesizer_with(handler, api_key=KEY, max_audio_bytes=3_000_000) -> AzureSynthesizer:
    http = BoundedHttpClient([URL], 1000, transport=httpx.MockTransport(handler))
    return AzureSynthesizer(http, AzureSpeechOptions(
        url=URL, api_key=SecretStr(api_key) if api_key else None, voice="es-CO-SalomeNeural", language="es-CO",
        output_format="riff-22050hz-16bit-mono-pcm", connect_timeout_seconds=1, read_timeout_seconds=1,
        max_audio_bytes=max_audio_bytes))


def test_sends_escaped_ssml_with_the_configured_voice_and_format():
    seen = {}
    wav = tone_wav(0.5, rate=22_050)

    def handler(request):
        seen["headers"] = request.headers
        seen["body"] = request.content.decode("utf-8")
        return httpx.Response(200, content=wav)

    audio = synthesizer_with(handler).synthesize("Baja el gas <ya> & espera.", 5)

    assert (audio.wav, audio.sample_rate_hz, audio.provider) == (wav, 22_050, "azure")
    assert seen["headers"]["ocp-apim-subscription-key"] == KEY
    assert seen["headers"]["content-type"] == "application/ssml+xml"
    assert seen["headers"]["x-microsoft-outputformat"] == "riff-22050hz-16bit-mono-pcm"
    assert seen["body"] == ("<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang=\"es-CO\">"
                            "<voice name=\"es-CO-SalomeNeural\">Baja el gas &lt;ya&gt; &amp; espera.</voice></speak>")


def test_reads_audio_larger_than_the_json_limit():
    wav = tone_wav(1.0, rate=22_050)

    assert synthesizer_with(lambda request: httpx.Response(200, content=wav)).synthesize("hola", 5).wav == wav


def test_does_not_call_azure_without_a_key():
    def handler(request):
        raise AssertionError("Azure should not be called")

    with pytest.raises(SynthesisUnavailable, match="key"):
        synthesizer_with(handler, api_key=None).synthesize("hola", 5)


@pytest.mark.parametrize("response", [
    httpx.Response(429), httpx.Response(401, text=KEY), httpx.Response(200, content=b"<html>"),
    httpx.Response(200, content=tone_wav(0.5, rate=22_050, channels=2)),
], ids=["429", "401", "not-audio", "stereo"])
def test_reports_azure_being_unavailable_without_leaking_the_key(response):
    with pytest.raises(SynthesisUnavailable) as error:
        synthesizer_with(lambda request: response).synthesize("hola", 5)

    assert KEY not in str(error.value)


def test_rejects_audio_over_the_size_limit():
    with pytest.raises(SynthesisUnavailable):
        synthesizer_with(lambda request: httpx.Response(200, content=tone_wav(1.0, rate=22_050)),
                         max_audio_bytes=1000).synthesize("hola", 5)
