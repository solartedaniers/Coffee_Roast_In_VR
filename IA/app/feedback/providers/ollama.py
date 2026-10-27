from dataclasses import dataclass

from app.feedback.providers.base import ProviderUnavailable
from app.net.bounded_http import BoundedHttpClient, HttpTimeout, RemoteServiceError

GENERATE_ENDPOINT = "/api/generate"
RESPONSE_FIELD = "response"
# phi4-mini a veces sigue generando tras una línea en blanco (repite instrucciones o inventa
# otra sección); "stop" lo evita casi siempre y la limpieza del texto es el respaldo.
STOP_SEQUENCES = ["\n\n"]


@dataclass(frozen=True)
class OllamaOptions:
    base_url: str
    model: str
    keep_alive: str
    temperature: float
    num_predict: int
    connect_timeout_seconds: float
    read_timeout_seconds: float


class OllamaFeedbackProvider:
    """Ollama local, con la misma petición que enviaba el servicio en Java."""

    name = "ollama"

    def __init__(self, http: BoundedHttpClient, options: OllamaOptions):
        self.http = http
        self.options = options
        self.url = options.base_url.rstrip("/") + GENERATE_ENDPOINT

    def request_body(self, prompt: str) -> dict:
        return {
            "model": self.options.model,
            "prompt": prompt,
            "stream": False,
            "keep_alive": self.options.keep_alive,
            "stop": STOP_SEQUENCES,
            "options": {"temperature": self.options.temperature, "num_predict": self.options.num_predict},
        }

    def generate(self, prompt: str, time_budget_seconds: float) -> str:
        timeout = HttpTimeout(min(self.options.connect_timeout_seconds, time_budget_seconds),
                              min(self.options.read_timeout_seconds, time_budget_seconds))
        try:
            result = self.http.post_json(self.url, self.request_body(prompt), {}, timeout)
        except RemoteServiceError as error:
            raise ProviderUnavailable(f"Ollama: {error}") from error
        text = result.get(RESPONSE_FIELD) if isinstance(result, dict) else None
        if not isinstance(text, str):
            raise ProviderUnavailable("Ollama returned no text")
        return text
