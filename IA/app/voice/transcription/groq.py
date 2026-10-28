from dataclasses import dataclass

from pydantic import SecretStr

from app.net.bounded_http import BoundedHttpClient, HttpTimeout, RemoteServiceError
from app.voice.transcription.base import TranscriptionUnavailable

TRANSCRIPTION_ENDPOINT = "/audio/transcriptions"


@dataclass(frozen=True)
class GroqTranscriptionOptions:
    base_url: str
    api_key: SecretStr | None
    model: str
    language: str
    prompt: str
    connect_timeout_seconds: float
    read_timeout_seconds: float


class GroqTranscriber:
    """Whisper en Groq (API compatible con OpenAI), en español y con temperatura 0."""

    name = "groq"

    def __init__(self, http: BoundedHttpClient, options: GroqTranscriptionOptions):
        self.http = http
        self.options = options
        self.url = options.base_url.rstrip("/") + TRANSCRIPTION_ENDPOINT

    def fields(self) -> dict[str, str]:
        return {"model": self.options.model, "language": self.options.language, "prompt": self.options.prompt,
                "response_format": "json", "temperature": "0"}

    def transcribe(self, wav: bytes, time_budget_seconds: float) -> str:
        api_key = self.options.api_key.get_secret_value() if self.options.api_key else ""
        if not api_key:
            raise TranscriptionUnavailable("Groq API key is not configured")
        timeout = HttpTimeout(min(self.options.connect_timeout_seconds, time_budget_seconds),
                              min(self.options.read_timeout_seconds, time_budget_seconds))
        try:
            result = self.http.post_multipart_json(self.url, self.fields(), ("question.wav", wav, "audio/wav"),
                                                   {"Authorization": f"Bearer {api_key}"}, timeout)
        except RemoteServiceError as error:
            raise TranscriptionUnavailable(f"Groq: {error}") from error
        text = result.get("text") if isinstance(result, dict) else None
        if not isinstance(text, str):
            raise TranscriptionUnavailable("Groq returned an unexpected transcription")
        return text
