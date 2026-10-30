import json
from dataclasses import dataclass

from pydantic import SecretStr

from app.evaluation.prompt import CriterionGrade, RubricPrompt
from app.evaluation.rubric import CriterionId
from app.net.bounded_http import BoundedHttpClient, HttpTimeout, RemoteServiceError

CHAT_ENDPOINT = "/chat/completions"


class GradingUnavailable(Exception):
    """Groq no devolvió una calificación utilizable (caído, sin cuota, sin llave o fuera del esquema)."""


@dataclass(frozen=True)
class GroqGraderOptions:
    base_url: str
    api_key: SecretStr | None
    model: str
    temperature: float
    seed: int
    max_completion_tokens: int
    reasoning_effort: str
    connect_timeout_seconds: float
    read_timeout_seconds: float


class GroqRubricGrader:
    """Sin respaldo con otro modelo: un modelo distinto podría dar otra nota a la misma sesión."""

    def __init__(self, http: BoundedHttpClient, options: GroqGraderOptions):
        self.http = http
        self.options = options
        self.url = options.base_url.rstrip("/") + CHAT_ENDPOINT

    @property
    def model(self) -> str:
        return self.options.model

    def request_body(self, prompt: RubricPrompt) -> dict:
        return {
            "model": self.options.model,
            "messages": [{"role": "system", "content": prompt.system}, {"role": "user", "content": prompt.user}],
            "temperature": self.options.temperature,
            "seed": self.options.seed,
            "max_completion_tokens": self.options.max_completion_tokens,
            "reasoning_effort": self.options.reasoning_effort,
            "include_reasoning": False,
            "response_format": {"type": "json_schema",
                                "json_schema": {"name": "roast_rubric", "strict": True, "schema": prompt.schema}},
        }

    def grade(self, prompt: RubricPrompt) -> dict[CriterionId, CriterionGrade]:
        criteria = self._complete(prompt)
        try:
            return self._read(criteria, prompt.schema)
        except (KeyError, IndexError, TypeError, ValueError):
            raise GradingUnavailable("Groq grade does not match its schema") from None

    def rewrite(self, prompt: RubricPrompt) -> dict[CriterionId, str]:
        """Solo el texto de las justificaciones: el nivel ya está decidido y no se vuelve a pedir."""
        criteria = self._complete(prompt)
        try:
            expected = prompt.schema["properties"]["criteria"]["required"]
            if set(criteria) != set(expected):
                raise ValueError("Unexpected criteria")
            texts = {CriterionId(criterion_id): criteria[criterion_id]["justification"] for criterion_id in expected}
            if not all(isinstance(text, str) and text.strip() for text in texts.values()):
                raise ValueError("Empty justification")
            return {criterion_id: text.strip() for criterion_id, text in texts.items()}
        except (KeyError, TypeError, ValueError):
            raise GradingUnavailable("Groq rewrite does not match its schema") from None

    def _complete(self, prompt: RubricPrompt) -> dict:
        api_key = self.options.api_key.get_secret_value() if self.options.api_key else ""
        if not api_key:
            raise GradingUnavailable("Groq API key is not configured")
        timeout = HttpTimeout(self.options.connect_timeout_seconds, self.options.read_timeout_seconds)
        try:
            result = self.http.post_json(self.url, self.request_body(prompt),
                                         {"Authorization": f"Bearer {api_key}"}, timeout)
        except RemoteServiceError as error:
            raise GradingUnavailable(f"Groq: {error}") from error
        try:
            criteria = json.loads(result["choices"][0]["message"]["content"])["criteria"]
        except (KeyError, IndexError, TypeError, ValueError):
            raise GradingUnavailable("Groq answer does not match its schema") from None
        if not isinstance(criteria, dict):
            raise GradingUnavailable("Groq answer does not match its schema")
        return criteria

    @staticmethod
    def _read(criteria: dict, schema: dict) -> dict[CriterionId, CriterionGrade]:
        # Se revisa igual que el esquema por si el proveedor no lo respeta del todo.
        expected = schema["properties"]["criteria"]["required"]
        allowed_levels = schema["properties"]["criteria"]["properties"][expected[0]]["properties"]["level"]["enum"]
        if set(criteria) != set(expected):
            raise ValueError("Unexpected criteria")
        grades = {}
        for criterion_id in expected:
            level, justification = criteria[criterion_id]["level"], criteria[criterion_id]["justification"]
            if type(level) is not int or level not in allowed_levels:
                raise ValueError("Level out of range")
            if not isinstance(justification, str) or not justification.strip():
                raise ValueError("Empty justification")
            grades[CriterionId(criterion_id)] = CriterionGrade(level, justification.strip())
        return grades
