import json
from dataclasses import dataclass

from pydantic import BaseModel, ConfigDict, Field, SecretStr, ValidationError

from app.net.bounded_http import BoundedHttpClient, HttpTimeout, RemoteServiceError
from app.voice.answer.prompt import AnswerPrompt

CHAT_ENDPOINT = "/chat/completions"

# Groq garantiza este esquema con strict (decodificación restringida) en los modelos gpt-oss.
ANSWER_SCHEMA = {
    "type": "object",
    "properties": {
        "sufficient": {"type": "boolean"},
        "answer": {"type": "string"},
        "sourceIds": {"type": "array", "items": {"type": "string"}},
    },
    "required": ["sufficient", "answer", "sourceIds"],
    "additionalProperties": False,
}


class AnswerUnavailable(Exception):
    """El modelo no respondió algo utilizable (caído, sin cuota, sin llave o fuera del esquema)."""


class ModelAnswer(BaseModel):
    model_config = ConfigDict(frozen=True, populate_by_name=True)

    sufficient: bool
    answer: str
    source_ids: list[str] = Field(alias="sourceIds")


@dataclass(frozen=True)
class GroqAnswerOptions:
    base_url: str
    api_key: SecretStr | None
    model: str
    temperature: float
    max_completion_tokens: int
    reasoning_effort: str
    connect_timeout_seconds: float
    read_timeout_seconds: float


class GroqVoiceAnswerer:
    name = "groq"

    def __init__(self, http: BoundedHttpClient, options: GroqAnswerOptions):
        self.http = http
        self.options = options
        self.url = options.base_url.rstrip("/") + CHAT_ENDPOINT

    def request_body(self, prompt: AnswerPrompt) -> dict:
        return {
            "model": self.options.model,
            "messages": [{"role": "system", "content": prompt.system}, {"role": "user", "content": prompt.user}],
            "temperature": self.options.temperature,
            "max_completion_tokens": self.options.max_completion_tokens,
            "reasoning_effort": self.options.reasoning_effort,
            "include_reasoning": False,
            "response_format": {"type": "json_schema",
                                "json_schema": {"name": "voice_answer", "strict": True, "schema": ANSWER_SCHEMA}},
        }

    def answer(self, prompt: AnswerPrompt, time_budget_seconds: float) -> ModelAnswer:
        api_key = self.options.api_key.get_secret_value() if self.options.api_key else ""
        if not api_key:
            raise AnswerUnavailable("Groq API key is not configured")
        timeout = HttpTimeout(min(self.options.connect_timeout_seconds, time_budget_seconds),
                              min(self.options.read_timeout_seconds, time_budget_seconds))
        try:
            result = self.http.post_json(self.url, self.request_body(prompt),
                                         {"Authorization": f"Bearer {api_key}"}, timeout)
        except RemoteServiceError as error:
            raise AnswerUnavailable(f"Groq: {error}") from error
        try:
            content = result["choices"][0]["message"]["content"]
            return ModelAnswer.model_validate(json.loads(content))
        except (KeyError, IndexError, TypeError, ValueError, ValidationError):
            raise AnswerUnavailable("Groq answer does not match its schema") from None
