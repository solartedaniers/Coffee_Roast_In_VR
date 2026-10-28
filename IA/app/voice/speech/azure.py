from dataclasses import dataclass
from xml.sax.saxutils import escape, quoteattr

from pydantic import SecretStr

from app.net.bounded_http import BoundedHttpClient, HttpTimeout, RemoteServiceError
from app.voice.audio import wave_sample_rate
from app.voice.speech.base import SynthesisUnavailable, SynthesizedAudio


@dataclass(frozen=True)
class AzureSpeechOptions:
    url: str
    api_key: SecretStr | None
    voice: str
    language: str
    output_format: str
    connect_timeout_seconds: float
    read_timeout_seconds: float
    max_audio_bytes: int


class AzureSynthesizer:
    """Voz neuronal de Azure por la API REST (sin SDK). El texto va escapado dentro del SSML."""

    name = "azure"

    def __init__(self, http: BoundedHttpClient, options: AzureSpeechOptions):
        self.http = http
        self.options = options

    def ssml(self, text: str) -> str:
        language = quoteattr(self.options.language)
        return (f"<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang={language}>"
                f"<voice name={quoteattr(self.options.voice)}>{escape(text)}</voice></speak>")

    def synthesize(self, text: str, time_budget_seconds: float) -> SynthesizedAudio:
        api_key = self.options.api_key.get_secret_value() if self.options.api_key else ""
        if not api_key:
            raise SynthesisUnavailable("Azure Speech key is not configured")
        headers = {"Ocp-Apim-Subscription-Key": api_key, "Content-Type": "application/ssml+xml",
                   "X-Microsoft-OutputFormat": self.options.output_format}
        timeout = HttpTimeout(min(self.options.connect_timeout_seconds, time_budget_seconds),
                              min(self.options.read_timeout_seconds, time_budget_seconds))
        try:
            wav = self.http.post_bytes(self.options.url, self.ssml(text).encode("utf-8"), headers, timeout,
                                       self.options.max_audio_bytes)
            return SynthesizedAudio(wav, wave_sample_rate(wav), self.name)
        except RemoteServiceError as error:
            raise SynthesisUnavailable(f"Azure: {error}") from error
        except ValueError:
            raise SynthesisUnavailable("Azure returned audio in an unexpected format") from None
