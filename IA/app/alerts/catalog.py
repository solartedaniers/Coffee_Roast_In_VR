"""Catálogo de alertas proactivas: reglas como datos, frases por nivel y política del canal de voz."""
from enum import StrEnum
from pathlib import Path

from pydantic import (BaseModel, ConfigDict, Field, StrictBool, StrictFloat, StrictInt, StrictStr,
                      model_validator)
from pydantic.alias_generators import to_camel

from app.domain import KnowledgeLevel

# Versión del formato que entiende el evaluador; cambia solo si cambia la semántica de las reglas.
SUPPORTED_SCHEMA_VERSION = 1


class RoastPhase(StrEnum):
    PREHEAT = "PREHEAT"
    READY = "READY"
    CHARGE_DIP = "CHARGE_DIP"
    ROASTING = "ROASTING"


class Signal(StrEnum):
    TEMPERATURE = "temperature"
    RISE_PER_MINUTE = "risePerMinute"
    STAGNATION_SECONDS = "stagnationSeconds"
    SECONDS_ABOVE_CEILING = "secondsAboveCeiling"
    DEVELOPMENT_RATIO = "developmentRatio"
    ROAST_SECONDS = "roastSeconds"
    MOISTURE_PCT = "moisturePct"
    PHASE = "phase"
    FIRST_CRACK_REACHED = "firstCrackReached"
    SECONDS_IN_PHASE = "secondsInPhase"
    PEAK_TEMPERATURE = "peakTemperature"
    TURNING_POINT_REACHED = "turningPointReached"


BOOLEAN_SIGNALS = frozenset({Signal.FIRST_CRACK_REACHED, Signal.TURNING_POINT_REACHED})


class Operator(StrEnum):
    EQ = "eq"
    NE = "ne"
    LT = "lt"
    LE = "le"
    GT = "gt"
    GE = "ge"


EQUALITY_OPERATORS = frozenset({Operator.EQ, Operator.NE})


class Priority(StrEnum):
    """En orden de importancia: el primero gana el canal de voz."""

    CRITICAL = "CRITICAL"
    HIGH = "HIGH"
    NORMAL = "NORMAL"
    LOW = "LOW"

    @property
    def rank(self) -> int:
        return list(Priority).index(self)


class CatalogModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, frozen=True, extra="forbid")


class Condition(CatalogModel):
    signal: Signal
    op: Operator
    value: StrictBool | StrictInt | StrictFloat | StrictStr

    @model_validator(mode="after")
    def value_matches_signal(self) -> "Condition":
        if self.signal in BOOLEAN_SIGNALS:
            valid = isinstance(self.value, bool) and self.op in EQUALITY_OPERATORS
        elif self.signal is Signal.PHASE:
            valid = self.value in set(RoastPhase) and self.op in EQUALITY_OPERATORS
        else:
            valid = isinstance(self.value, (int, float)) and not isinstance(self.value, bool)
        if not valid:
            raise ValueError(f"Invalid condition {self.signal} {self.op} {self.value!r}")
        return self


class AlertRule(CatalogModel):
    id: StrictStr = Field(min_length=1)
    priority: Priority
    levels: tuple[KnowledgeLevel, ...] = Field(min_length=1)
    when: tuple[Condition, ...] = Field(min_length=1)
    sustain_seconds: StrictInt = Field(ge=1)
    # None: se re-arma cuando deja de cumplirse 'when'. Lista vacía: queda armada de inmediato.
    rearm_when: tuple[Condition, ...] | None
    cooldown_seconds: StrictInt = Field(ge=0)
    max_repeats: StrictInt = Field(ge=1)
    requires_quiet_seconds: StrictInt = Field(ge=0)
    source: StrictStr = Field(min_length=1)
    phrases: dict[KnowledgeLevel, StrictStr]

    @model_validator(mode="after")
    def one_phrase_per_level(self) -> "AlertRule":
        if len(set(self.levels)) != len(self.levels):
            raise ValueError(f"{self.id} repeats a level")
        if set(self.phrases) != set(self.levels) or not all(text.strip() for text in self.phrases.values()):
            raise ValueError(f"{self.id} needs exactly one non-empty phrase per level")
        return self


class SignalSettings(CatalogModel):
    rise_window_seconds: StrictInt = Field(gt=0)
    maillard_start_temperature: float
    maillard_end_temperature: float
    burn_ceiling_temperature: float
    turning_point_min_rise: float = Field(ge=0)

    @model_validator(mode="after")
    def maillard_range_is_ordered(self) -> "SignalSettings":
        if self.maillard_start_temperature >= self.maillard_end_temperature:
            raise ValueError("The Maillard range must start below its end")
        return self


class ChannelPolicy(CatalogModel):
    queue_max_wait_seconds: StrictInt = Field(ge=0)
    min_gap_seconds: StrictInt = Field(ge=0)
    # Suenan sin esperar la separación mínima y aunque el asistente esté respondiendo.
    urgent_priorities: frozenset[Priority]
    # Si no pueden sonar en el momento se descartan en vez de esperar en la cola.
    unqueued_priorities: frozenset[Priority]


class AlertCatalog(CatalogModel):
    description: StrictStr = ""
    schema_version: StrictInt
    catalog_revision: StrictInt = Field(ge=1)
    default_level: KnowledgeLevel
    signals: SignalSettings
    channel: ChannelPolicy
    rules: tuple[AlertRule, ...] = Field(min_length=1)

    @model_validator(mode="after")
    def supported_and_unique(self) -> "AlertCatalog":
        if self.schema_version != SUPPORTED_SCHEMA_VERSION:
            raise ValueError(f"Unsupported alert schema version {self.schema_version}")
        ids = [rule.id for rule in self.rules]
        if len(set(ids)) != len(ids):
            raise ValueError("Alert rule ids must be unique")
        return self

    @classmethod
    def from_file(cls, path: Path) -> "AlertCatalog":
        return cls.model_validate_json(path.read_text(encoding="utf-8"))

    def rules_for(self, level: KnowledgeLevel) -> tuple[AlertRule, ...]:
        return tuple(rule for rule in self.rules if level in rule.levels)
