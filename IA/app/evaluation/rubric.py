"""Rúbrica de las decisiones del jugador (config/roast_rubric.json): criterios, niveles y parámetros."""
import re
from enum import StrEnum
from pathlib import Path

from pydantic import BaseModel, ConfigDict, Field, model_validator
from pydantic.alias_generators import to_camel


class CriterionId(StrEnum):
    DRYING_GAS = "DRYING_GAS"
    RISE_TO_FIRST_CRACK = "RISE_TO_FIRST_CRACK"
    GAS_AFTER_FIRST_CRACK = "GAS_AFTER_FIRST_CRACK"
    ALERT_REACTION = "ALERT_REACTION"


class Evidence(StrEnum):
    SOURCED = "SOURCED"
    INFERENCE = "INFERENCE"
    DESIGN_PARAMETER = "DESIGN_PARAMETER"


class RubricStatus(StrEnum):
    DRAFT = "DRAFT"
    APPROVED = "APPROVED"


class ExpectedReaction(StrEnum):
    LOWER_GAS = "LOWER_GAS"
    RAISE_GAS = "RAISE_GAS"
    LOWER_GAS_OR_DISCHARGE = "LOWER_GAS_OR_DISCHARGE"
    DISCHARGE = "DISCHARGE"


class RubricModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, frozen=True, extra="forbid")


class RubricParameters(RubricModel):
    rise_window_seconds: int = Field(gt=0)
    turning_point_min_rise: float = Field(gt=0)
    drying_end_temperature: float
    drying_rise_min: float
    drying_rise_max: float
    drying_in_band_high: float = Field(ge=0, le=100)
    drying_in_band_low: float = Field(ge=0, le=100)
    drying_quick_correction_seconds: int = Field(gt=0)
    drying_slow_correction_seconds: int = Field(gt=0)
    stall_short_seconds: int = Field(gt=0)
    stall_medium_seconds: int = Field(gt=0)
    stall_baked_seconds: int = Field(gt=0)
    first_crack_min_minutes: float = Field(gt=0)
    first_crack_max_minutes: float = Field(gt=0)
    first_crack_slack_minutes: float = Field(ge=0)
    gas_drop_min_points: float = Field(gt=0)
    gas_drop_quick_seconds: int = Field(gt=0)
    gas_drop_late_seconds: int = Field(gt=0)
    gas_drop_anticipation_seconds: int = Field(ge=0)
    min_development_seconds: int = Field(ge=0)
    burn_warning_temperature: float
    alert_reaction_seconds: int = Field(gt=0)
    alert_reacted_high: float = Field(ge=0, le=100)
    alert_reacted_medium: float = Field(ge=0, le=100)
    alert_reacted_low: float = Field(ge=0, le=100)
    justification_max_chars: int = Field(gt=0)
    justification_min_chars: int = Field(gt=0)

    @model_validator(mode="after")
    def ranges_are_ordered(self) -> "RubricParameters":
        ordered = (
            self.drying_rise_min < self.drying_rise_max
            and self.drying_in_band_low <= self.drying_in_band_high
            and self.drying_quick_correction_seconds <= self.drying_slow_correction_seconds
            and self.stall_short_seconds <= self.stall_medium_seconds <= self.stall_baked_seconds
            and self.first_crack_min_minutes < self.first_crack_max_minutes
            and self.gas_drop_quick_seconds <= self.gas_drop_late_seconds
            and self.alert_reacted_low <= self.alert_reacted_medium <= self.alert_reacted_high
            and self.justification_min_chars < self.justification_max_chars
        )
        if not ordered:
            raise ValueError("Rubric parameters must keep their ranges in order")
        return self

    def placeholders(self) -> dict[str, str]:
        """Valores para los {parametro} de los textos, sin ".0" en los enteros."""
        return {to_camel(name): f"{value:g}" for name, value in self.model_dump().items()}


class PromptLabels(RubricModel):
    criterion: str = Field(min_length=1)
    description: str = Field(min_length=1)
    levels: str = Field(min_length=1)
    facts: str = Field(min_length=1)
    assigned_level: str = Field(min_length=1)
    previous_justification: str = Field(min_length=1)


class Criterion(RubricModel):
    id: CriterionId
    title: str = Field(min_length=1)
    description: str = Field(min_length=1)
    evidence: Evidence
    source: str = Field(min_length=1)
    levels: dict[int, str]
    # Lo que ve el jugador si el modelo no da una justificación legible para él.
    fallback_justifications: dict[int, str]


class Rubric(RubricModel):
    description: str
    rubric_version: str = Field(min_length=1)
    status: RubricStatus
    max_level: int = Field(gt=0)
    parameters: RubricParameters
    alert_reactions: dict[str, ExpectedReaction]
    reaction_labels: dict[ExpectedReaction, str]
    prompt_labels: PromptLabels
    instructions: list[str] = Field(min_length=1)
    rewrite_instruction: str = Field(min_length=1)
    # Términos de la rúbrica que el jugador no debe ver en una justificación (sin distinguir mayúsculas).
    justification_forbidden_patterns: list[str] = Field(default_factory=list)
    criteria: list[Criterion] = Field(min_length=1)

    @model_validator(mode="after")
    def criteria_are_complete(self) -> "Rubric":
        ids = [criterion.id for criterion in self.criteria]
        if len(ids) != len(set(ids)):
            raise ValueError("Rubric criteria must not repeat")
        expected_levels = set(range(self.max_level + 1))
        for criterion in self.criteria:
            if set(criterion.levels) != expected_levels:
                raise ValueError(f"{criterion.id}: levels must be 0 to {self.max_level}")
            if set(criterion.fallback_justifications) != expected_levels:
                raise ValueError(f"{criterion.id}: fallback justifications must cover levels 0 to {self.max_level}")
        if set(self.reaction_labels) != set(ExpectedReaction):
            raise ValueError("Every expected reaction needs a label")
        for pattern in self.justification_forbidden_patterns:
            try:
                re.compile(pattern)
            except re.error as error:
                raise ValueError(f"Invalid forbidden justification pattern {pattern!r}") from error
        self.render("".join(self.instructions) + self.rewrite_instruction)
        for criterion in self.criteria:
            for text in (criterion.description, criterion.source, *criterion.levels.values(),
                         *criterion.fallback_justifications.values()):
                self.render(text)
        return self

    @classmethod
    def from_file(cls, path: Path) -> "Rubric":
        return cls.model_validate_json(path.read_text(encoding="utf-8"))

    def render(self, text: str) -> str:
        """Reemplaza los {parametro}; un nombre desconocido falla al cargar la rúbrica, no al evaluar."""
        values = self.parameters.placeholders() | {"maxLevel": str(self.max_level)}
        try:
            return text.format_map(values)
        except (KeyError, ValueError) as error:
            raise ValueError(f"Unknown rubric placeholder in {text!r}") from error

    def criterion(self, criterion_id: CriterionId) -> Criterion | None:
        return next((criterion for criterion in self.criteria if criterion.id is criterion_id), None)
