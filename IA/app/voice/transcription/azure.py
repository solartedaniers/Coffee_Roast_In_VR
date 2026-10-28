import json
from dataclasses import dataclass
from urllib.parse import urlencode

from pydantic import SecretStr

from app.net.bounded_http import BoundedHttpClient, HttpTimeout, RemoteServiceError
from app.voice.transcription.base import NoSpeechDetected, TranscriptionUnavailable

# RecognitionStatus de la API REST para audio corto de Azure.
SUCCESS = "Success"
NO_SPEECH_STATUSES = frozenset({"NoMatch", "InitialSilenceTimeout", "BabbleTimeout"})


@dataclass(frozen=True)
class AzureTranscriptionOptions:
    url: str
    api_key: SecretStr | None
    language: str
    sample_rate_hz: int
    connect_timeout_seconds: float
    read_timeout_seconds: float
    max_response_bytes: int


class AzureTranscriber:
    """API REST de Azure para audio corto (hasta 60 s), el respaldo de Whisper."""

    name = "azure"

    def __init__(self, http: BoundedHttpClient, options: AzureTranscriptionOptions):
        self.http = http
        self.options = options
        self.url = options.url + "?" + urlencode({"language": options.language, "format": "simple"})

    def transcribe(self, wav: bytes, time_budget_seconds: float) -> str:
        api_key = self.options.api_key.get_secret_value() if self.options.api_key else ""
        if not api_key:
            raise TranscriptionUnavailable("Azure Speech key is not configured")
        headers = {
            "Ocp-Apim-Subscription-Key": api_key,
            "Content-Type": f"audio/wav; codecs=audio/pcm; samplerate={self.options.sample_rate_hz}",
            "Accept": "application/json",
        }
        timeout = HttpTimeout(min(self.options.connect_timeout_seconds, time_budget_seconds),
                              min(self.options.read_timeout_seconds, time_budget_seconds))
        try:
            raw = self.http.post_bytes(self.url, wav, headers, timeout, self.options.max_response_bytes)
            result = json.loads(raw)
        except RemoteServiceError as error:
            raise TranscriptionUnavailable(f"Azure: {error}") from error
        except (ValueError, UnicodeError):
            raise TranscriptionUnavailable("Azure returned invalid JSON") from None
        status = result.get("RecognitionStatus") if isinstance(result, dict) else None
        if status in NO_SPEECH_STATUSES:
            raise NoSpeechDetected(status)
        text = result.get("DisplayText") if status == SUCCESS else None
        if not isinstance(text, str):
            raise TranscriptionUnavailable(f"Azure recognition failed ({status})")
        return text
