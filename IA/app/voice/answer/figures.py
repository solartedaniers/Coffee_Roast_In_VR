"""Quita de la respuesta las frases con cifras que no salen de la evidencia.

Es un simulador para enseñar: un porcentaje de gas o una temperatura inventada enseña algo falso.
"""
import logging
import re

log = logging.getLogger(__name__)
SENTENCE_BOUNDARY = re.compile(r"(?<=[.!?…])\s+")
NUMBER = re.compile(r"(?<!\w)\d+(?:[.,]\d+)?")


def numbers_in(text: str) -> list[float]:
    return [float(token.replace(",", ".")) for token in NUMBER.findall(text)]


class SupportedFigures:
    # ponytail: solo reconoce cifras escritas con dígitos (el prompt lo exige); "setenta por ciento" pasaría.
    def __init__(self, tolerance: float):
        self.tolerance = tolerance

    def keep_supported(self, text: str, evidence: str) -> str:
        allowed = numbers_in(evidence)
        kept = []
        for sentence in SENTENCE_BOUNDARY.split(text):
            unsupported = [n for n in numbers_in(sentence)
                           if not any(abs(n - value) <= self.tolerance for value in allowed)]
            if unsupported:
                log.warning("Dropping sentence with unsupported figures %s", unsupported)
            else:
                kept.append(sentence)
        return " ".join(kept)
