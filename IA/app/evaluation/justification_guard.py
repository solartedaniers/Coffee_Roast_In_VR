"""Revisa que la justificación que ve el jugador no traiga nombres internos de los hechos, "nombre = valor" ni
términos de la rúbrica (niveles, requisitos, franja), que se configuran en la rúbrica.

Limpia lo que se puede quitar sin cambiar el sentido (paréntesis técnicos, asignaciones sueltas, decimales de
temperatura y de minutos). Si después de limpiar sigue habiendo algo interno, la justificación no sirve.
"""
import math
import re
from dataclasses import dataclass

from app.evaluation.facts import Facts
from app.evaluation.rubric import Rubric

CAMEL_CASE = re.compile(r"\b[a-z]+(?:[A-Z][a-z0-9]*)+\b")
UPPER_SNAKE = re.compile(r"\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+\b")
LITERALS = re.compile(r"\b(?:true|false|null|None)\b")
ASSIGNMENT = re.compile(r"[\w.\-]+\s*=\s*[^\s,;)]*")
PARENTHESIS = re.compile(r"\s*\(([^()]*)\)")
DECIMAL_CELSIUS = re.compile(r"\b(\d+)[.,](\d+)\s*°\s*C")
DECIMAL_MINUTES = re.compile(r"\b(\d+)[.,](\d+)\s*min\b")
SPACE_BEFORE_PUNCTUATION = re.compile(r"\s+([,.;:])")
REPEATED_PUNCTUATION = re.compile(r"([,;])\s*[,;]+")
COMMA_BEFORE_PERIOD = re.compile(r"[,;]\s*\.")
SPACES = re.compile(r"\s{2,}")


@dataclass(frozen=True)
class ReviewedJustification:
    text: str | None  # None: no se pudo dejar legible para el jugador
    cleaned: bool


def fact_names(facts: Facts) -> set[str]:
    """Nombres de los hechos, también los anidados, y sus valores internos en mayúsculas (FAST, IN_BAND)."""
    names = set()
    stack: list[object] = [facts]
    while stack:
        item = stack.pop()
        if isinstance(item, dict):
            names.update(item)
            stack.extend(item.values())
        elif isinstance(item, list):
            stack.extend(item)
        elif isinstance(item, str) and item.isupper():
            names.add(item)
    return names


def round_half_up(value: float) -> int:
    return math.floor(value + 0.5)


class JustificationGuard:
    def __init__(self, rubric: Rubric):
        self.min_chars = rubric.parameters.justification_min_chars
        self.internal_names = ({str(criterion.id) for criterion in rubric.criteria} | set(rubric.alert_reactions)
                               | {str(reaction) for reaction in rubric.reaction_labels})
        self.forbidden = [re.compile(pattern, re.IGNORECASE) for pattern in rubric.justification_forbidden_patterns]

    def has_internal_names(self, text: str, facts: Facts | None = None) -> bool:
        names = self.internal_names | (fact_names(facts) if facts else set())
        words = set(re.findall(r"\w+", text))
        return bool(words & names or CAMEL_CASE.search(text) or UPPER_SNAKE.search(text)
                    or LITERALS.search(text) or "=" in text
                    or any(pattern.search(text) for pattern in self.forbidden))

    def review(self, text: str, facts: Facts | None = None) -> ReviewedJustification:
        cleaned = self._clean(text, facts)
        usable = len(cleaned) >= self.min_chars and not self.has_internal_names(cleaned, facts)
        return ReviewedJustification(cleaned if usable else None, cleaned != text.strip())

    def _clean(self, text: str, facts: Facts | None) -> str:
        text = PARENTHESIS.sub(lambda match: "" if self._is_technical(match.group(1), facts) else match.group(0),
                               text)
        text = ASSIGNMENT.sub("", text)
        text = DECIMAL_CELSIUS.sub(lambda match: f"{round_half_up(float(match.group(1) + '.' + match.group(2)))} °C",
                                   text)
        text = DECIMAL_MINUTES.sub(lambda match: self._minutes_and_seconds(match.group(1), match.group(2)), text)
        text = SPACE_BEFORE_PUNCTUATION.sub(r"\1", text)
        text = REPEATED_PUNCTUATION.sub(r"\1", text)
        text = COMMA_BEFORE_PERIOD.sub(".", text)
        return SPACES.sub(" ", text).strip()

    def _is_technical(self, inside: str, facts: Facts | None) -> bool:
        return not inside.strip() or self.has_internal_names(inside, facts)

    @staticmethod
    def _minutes_and_seconds(whole: str, fraction: str) -> str:
        total = round_half_up(float(f"{whole}.{fraction}") * 60)
        minutes, seconds = divmod(total, 60)
        return f"{minutes} min {seconds} s" if seconds else f"{minutes} min"
