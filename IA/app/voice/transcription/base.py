from typing import Protocol


class TranscriptionUnavailable(Exception):
    """El proveedor no pudo transcribir (caído, sin cuota, sin llave o respuesta inválida)."""


class NoSpeechDetected(Exception):
    """El proveedor respondió bien pero el audio no tiene habla."""


class Transcriber(Protocol):
    name: str

    def transcribe(self, wav: bytes, time_budget_seconds: float) -> str:
        """Texto reconocido, o lanza TranscriptionUnavailable o NoSpeechDetected."""
        ...
