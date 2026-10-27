from typing import Protocol


class ProviderUnavailable(Exception):
    """El proveedor no pudo generar texto (caído, sin cuota, sin llave o respuesta inválida)."""


class FeedbackProvider(Protocol):
    name: str

    def generate(self, prompt: str, time_budget_seconds: float) -> str:
        """Devuelve el texto crudo del modelo o lanza ProviderUnavailable."""
        ...
