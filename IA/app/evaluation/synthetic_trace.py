"""Trazas de prueba por puntos clave, para los tests y los casos de validación de la rúbrica.

Los puntos van en segundos del tueste y se interpolan en línea recta, igual que las curvas de las alertas.
"""
from pydantic import BaseModel, ConfigDict, Field, model_validator
from pydantic.alias_generators import to_camel

from app.alerts.synthetic_curve import TEMPERATURE_DECIMALS, interpolate
from app.evaluation.contracts import (GasChange, OperationMode, RoastTrace, SUPPORTED_TRACE_VERSION, TraceAlert,
                                      TraceEvent, TraceEventType, TraceSample)


class SyntheticTrace(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, frozen=True, extra="forbid")

    id: str = Field(min_length=1)
    description: str
    operation_mode: OperationMode = OperationMode.MANUAL
    duration_seconds: int = Field(gt=0)
    sample_interval_seconds: int = Field(default=5, gt=0)
    points: list[tuple[int, float]] = Field(min_length=2)
    initial_gas: float = Field(ge=0, le=100)
    gas_changes: list[tuple[int, float]] = []
    first_crack_second: int | None = None
    second_crack_second: int | None = None
    alerts: list[tuple[str, int]] = []

    @model_validator(mode="after")
    def points_cover_the_roast(self) -> "SyntheticTrace":
        positions = [position for position, _ in self.points]
        if positions != sorted(set(positions)) or positions[0] != 0 or positions[-1] != self.duration_seconds:
            raise ValueError(f"{self.id}: points must be increasing from 0 to durationSeconds")
        return self

    def gas_at(self, second: int) -> float:
        gas = self.initial_gas
        for t, value in self.gas_changes:
            if t <= second:
                gas = value
        return gas

    def trace(self) -> RoastTrace:
        seconds = list(range(0, self.duration_seconds, self.sample_interval_seconds)) + [self.duration_seconds]
        events = [TraceEvent(type=TraceEventType.CHARGE, t=0)]
        if self.first_crack_second is not None:
            events.append(TraceEvent(type=TraceEventType.FIRST_CRACK, t=self.first_crack_second))
        if self.second_crack_second is not None:
            events.append(TraceEvent(type=TraceEventType.SECOND_CRACK, t=self.second_crack_second))
        events.append(TraceEvent(type=TraceEventType.DISCHARGE, t=self.duration_seconds))
        return RoastTrace(
            version=SUPPORTED_TRACE_VERSION,
            operation_mode=self.operation_mode,
            sample_interval_seconds=self.sample_interval_seconds,
            samples=[TraceSample(t=t, bean=round(interpolate(self.points, t), TEMPERATURE_DECIMALS),
                                 gas=self.gas_at(t)) for t in seconds],
            gas_changes=[GasChange(t=t, gas=gas) for t, gas in self.gas_changes],
            events=events,
            alerts=[TraceAlert(id=alert_id, t=t) for alert_id, t in self.alerts],
        )
