"""Contrato JSON de la evaluación con el backend. La traza es la que guarda el backend al recibir la sesión.

t siempre es el segundo del tueste (roastingElapsedSeconds): cero en la carga. La especificación para
Unity está en docs/unity-roast-trace-contract.md.
"""
from enum import StrEnum

from pydantic import Field, model_validator

from app.contracts import CamelCaseModel
from app.evaluation.rubric import CriterionId

SUPPORTED_TRACE_VERSION = 1


class OperationMode(StrEnum):
    MANUAL = "MANUAL"
    AUTO = "AUTO"


class TraceEventType(StrEnum):
    CHARGE = "CHARGE"
    FIRST_CRACK = "FIRST_CRACK"
    SECOND_CRACK = "SECOND_CRACK"
    DISCHARGE = "DISCHARGE"


class TraceModel(CamelCaseModel):
    model_config = CamelCaseModel.model_config | {"frozen": True}


class TraceSample(TraceModel):
    t: int = Field(ge=0)
    bean: float
    gas: float = Field(ge=0, le=100)


class GasChange(TraceModel):
    t: int = Field(ge=0)
    gas: float = Field(ge=0, le=100)


class TraceEvent(TraceModel):
    type: TraceEventType
    t: int = Field(ge=0)


class TraceAlert(TraceModel):
    id: str = Field(min_length=1)
    t: int = Field(ge=0)


class RoastTrace(TraceModel):
    version: int
    operation_mode: OperationMode
    sample_interval_seconds: int = Field(gt=0)
    samples: list[TraceSample] = Field(min_length=2)
    gas_changes: list[GasChange] = []
    events: list[TraceEvent] = []
    alerts: list[TraceAlert] = []

    @model_validator(mode="after")
    def is_chronological(self) -> "RoastTrace":
        if self.version != SUPPORTED_TRACE_VERSION:
            raise ValueError(f"Unsupported trace version {self.version}")
        times = [sample.t for sample in self.samples]
        if times != sorted(set(times)):
            raise ValueError("Samples must be strictly increasing in time")
        gas_times = [change.t for change in self.gas_changes]
        if gas_times != sorted(gas_times):
            raise ValueError("Gas changes must be in chronological order")
        return self

    def event_second(self, event_type: TraceEventType) -> int | None:
        return next((event.t for event in self.events if event.type is event_type), None)


class EvaluationRequest(TraceModel):
    trace: RoastTrace


class EvaluationStatus(StrEnum):
    COMPLETED = "COMPLETED"
    NOT_APPLICABLE = "NOT_APPLICABLE"


class CriterionScore(TraceModel):
    id: CriterionId
    level: int = Field(ge=0)
    max_level: int = Field(gt=0)
    justification: str


class EvaluationResponse(TraceModel):
    status: EvaluationStatus
    rubric_version: str
    # None cuando ningún criterio aplica y no se llamó al modelo.
    model: str | None
    score: float | None
    criteria: list[CriterionScore]
    not_applicable: list[CriterionId]
