"""Curvas de prueba por puntos clave (IA/eval/alerts/curves.json).

Los puntos salen de curvas del simulador del frontend y se interpolan en línea recta, una muestra por
segundo simulado. Las muestras se indexan desde la primera de READY.
"""
import bisect
from pathlib import Path

from pydantic import BaseModel, ConfigDict, Field, model_validator
from pydantic.alias_generators import to_camel

from app.alerts.catalog import RoastPhase
from app.alerts.signals import RoastSample

TEMPERATURE_DECIMALS = 2
MOISTURE_DECIMALS = 3


def interpolate(points: list[tuple[int, float]], index: int) -> float:
    positions = [position for position, _ in points]
    right = bisect.bisect_left(positions, index)
    if right == 0:
        return points[0][1]
    if right == len(points):
        return points[-1][1]
    (x0, y0), (x1, y1) = points[right - 1], points[right]
    return y0 + (y1 - y0) * (index - x0) / (x1 - x0)


class SyntheticCurve(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, frozen=True, extra="forbid")

    id: str = Field(min_length=1)
    description: str
    samples: int = Field(gt=0)
    ready_samples: int = Field(ge=0)
    charge_dip_samples: int = Field(ge=0)
    first_crack_sample: int | None
    points: list[tuple[int, float]] = Field(min_length=2)
    moisture_points: list[tuple[int, float]] | None = None
    # Rangos de muestras, ambos extremos incluidos.
    recording: list[tuple[int, int]] = []
    assistant_busy: list[tuple[int, int]] = []

    @model_validator(mode="after")
    def points_cover_the_curve(self) -> "SyntheticCurve":
        positions = [position for position, _ in self.points]
        if positions != sorted(set(positions)) or positions[0] != 0 or positions[-1] != self.samples - 1:
            raise ValueError(f"{self.id}: points must be increasing from 0 to samples - 1")
        return self

    def roast_samples(self) -> list[RoastSample]:
        return [self._sample(index) for index in range(self.samples)]

    def is_recording(self, index: int) -> bool:
        return any(start <= index <= end for start, end in self.recording)

    def is_assistant_busy(self, index: int) -> bool:
        return any(start <= index <= end for start, end in self.assistant_busy)

    def _sample(self, index: int) -> RoastSample:
        charge_index = self.ready_samples
        if index < charge_index:
            phase = RoastPhase.READY
        elif index < charge_index + self.charge_dip_samples:
            phase = RoastPhase.CHARGE_DIP
        else:
            phase = RoastPhase.ROASTING
        moisture = None if self.moisture_points is None else round(
            interpolate(self.moisture_points, index), MOISTURE_DECIMALS)
        return RoastSample(
            phase=phase,
            temperature=round(interpolate(self.points, index), TEMPERATURE_DECIMALS),
            roast_seconds=0 if phase is RoastPhase.READY else index - charge_index + 1,
            first_crack_reached=self.first_crack_sample is not None and index >= self.first_crack_sample,
            moisture_pct=moisture,
        )


class CurveSet(BaseModel):
    description: str
    curves: list[SyntheticCurve] = Field(min_length=1)

    @classmethod
    def from_file(cls, path: Path) -> "CurveSet":
        return cls.model_validate_json(path.read_text(encoding="utf-8"))
