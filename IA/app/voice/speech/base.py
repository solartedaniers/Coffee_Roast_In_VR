from dataclasses import dataclass
from typing import Protocol


class SynthesisUnavailable(Exception):
    """El proveedor no pudo generar el audio (caído, sin cuota, sin instalar o audio inválido)."""


@dataclass(frozen=True)
class SynthesizedAudio:
    wav: bytes
    sample_rate_hz: int
    provider: str


class Synthesizer(Protocol):
    name: str

    def synthesize(self, text: str, time_budget_seconds: float) -> SynthesizedAudio:
        """WAV PCM16 mono con el texto hablado, o lanza SynthesisUnavailable."""
        ...
