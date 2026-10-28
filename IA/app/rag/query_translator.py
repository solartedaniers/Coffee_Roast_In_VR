import logging

from app.feedback.providers.base import FeedbackProvider

log = logging.getLogger(__name__)

TRANSLATION_PROMPT = (
    "Translate the following coffee roasting search query into {language}. Keep technical terms "
    "(first crack, Agtron, Maillard) as they are. Answer with the translation only, in one line, "
    "without quotes or explanations.\n\n{query}"
)
QUOTES = "\"'“”«»"


class QueryTranslator:
    """Traduce la búsqueda con un LLM para encontrar también los PDFs en otro idioma."""

    def __init__(self, provider: FeedbackProvider, target_language: str, time_budget_seconds: float):
        self.provider = provider
        self.target_language = target_language
        self.time_budget_seconds = time_budget_seconds

    def translate(self, query: str) -> str | None:
        """None si el proveedor falla: la búsqueda sigue solo con el texto original."""
        prompt = TRANSLATION_PROMPT.format(language=self.target_language, query=query)
        try:
            raw = self.provider.generate(prompt, self.time_budget_seconds)
        except Exception as error:
            log.warning("Query translation failed, searching only the original text: %s", error)
            return None
        lines = [line.strip().strip(QUOTES).strip() for line in raw.splitlines()]
        return next((line for line in lines if line), None)
