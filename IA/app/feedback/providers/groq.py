from dataclasses import dataclass

from pydantic import SecretStr

from app.feedback.providers.base import ProviderUnavailable
from app.net.bounded_http import BoundedHttpClient, HttpTimeout, RemoteServiceError

CHAT_ENDPOINT = "/chat/completions"


@dataclass(frozen=True)
class GroqOptions:
    base_url: str
    api_key: SecretStr | None
    model: str
    temperature: float
    max_completion_tokens: int
    reasoning_effort: str
    connect_timeout_seconds: float
    read_timeout_seconds: float


class GroqFeedbackProvider:
    """Groq (API compatible con OpenAI). El prompt va completo como un solo mensaje de usuario,
    igual que con Ollama, para no cambiar la instrucción que ya estaba calibrada."""

    name = "groq"

    def __init__(self, http: BoundedHttpClient, options: GroqOptions):
        self.http = http
        self.options = options
        self.url = options.base_url.rstrip("/") + CHAT_ENDPOINT

    def request_body(self, prompt: str) -> dict:
        # Sin "stop": Groq no garantiza que funcione con gpt-oss; la limpieza del texto corta igual.
        return {
            "model": self.options.model,
            "messages": [{"role": "user", "content": prompt}],
            "temperature": self.options.temperature,
            "max_completion_tokens": self.options.max_completion_tokens,
            "reasoning_effort": self.options.reasoning_effort,
            "include_reasoning": False,
        }

    def generate(self, prompt: str, time_budget_seconds: float) -> str:
        api_key = self.options.api_key.get_secret_value() if self.options.api_key else ""
        if not api_key:
            raise ProviderUnavailable("Groq API key is not configured")
        timeout = HttpTimeout(min(self.options.connect_timeout_seconds, time_budget_seconds),
                              min(self.options.read_timeout_seconds, time_budget_seconds))
        try:
            result = self.http.post_json(self.url, self.request_body(prompt),
                                         {"Authorization": f"Bearer {api_key}"}, timeout)
        except RemoteServiceError as error:
            raise ProviderUnavailable(f"Groq: {error}") from error
        try:
            text = result["choices"][0]["message"]["content"]
        except (KeyError, IndexError, TypeError):
            raise ProviderUnavailable("Groq returned an unexpected response") from None
        # Si el razonamiento agota max_completion_tokens el contenido llega vacío.
        if not isinstance(text, str) or not text.strip():
            raise ProviderUnavailable("Groq returned no text")
        return text
