"""Señales que evalúan las reglas, calculadas igual que el simulador del frontend.

Cada muestra es un segundo simulado (un tick del simulador). Unity debe calcularlas igual; la
especificación está en docs/unity-proactive-alerts-contract.md.
"""
import math
from collections import deque
from dataclasses import dataclass

from app.alerts.catalog import RoastPhase, Signal, SignalSettings

CHARGED_PHASES = frozenset({RoastPhase.CHARGE_DIP, RoastPhase.ROASTING})

SignalValue = float | int | bool | str | None


@dataclass(frozen=True)
class RoastSample:
    """Lo que Unity tiene en cada tick. temperature es la que muestra el termómetro: el aire antes
    de cargar y el grano después. roast_seconds es roastingElapsedSeconds, el reloj de la nota."""

    phase: RoastPhase
    temperature: float
    roast_seconds: int
    first_crack_reached: bool
    moisture_pct: float | None = None


@dataclass(frozen=True)
class RoastSignals:
    temperature: float
    rise_per_minute: int
    stagnation_seconds: int
    seconds_above_ceiling: int
    development_ratio: float
    roast_seconds: int
    moisture_pct: float | None
    phase: RoastPhase
    first_crack_reached: bool
    seconds_in_phase: int
    peak_temperature: float | None
    turning_point_reached: bool

    def value(self, signal: Signal) -> SignalValue:
        return getattr(self, SIGNAL_ATTRIBUTES[signal])


SIGNAL_ATTRIBUTES = {
    Signal.TEMPERATURE: "temperature",
    Signal.RISE_PER_MINUTE: "rise_per_minute",
    Signal.STAGNATION_SECONDS: "stagnation_seconds",
    Signal.SECONDS_ABOVE_CEILING: "seconds_above_ceiling",
    Signal.DEVELOPMENT_RATIO: "development_ratio",
    Signal.ROAST_SECONDS: "roast_seconds",
    Signal.MOISTURE_PCT: "moisture_pct",
    Signal.PHASE: "phase",
    Signal.FIRST_CRACK_REACHED: "first_crack_reached",
    Signal.SECONDS_IN_PHASE: "seconds_in_phase",
    Signal.PEAK_TEMPERATURE: "peak_temperature",
    Signal.TURNING_POINT_REACHED: "turning_point_reached",
}


def js_round(value: float) -> int:
    """Math.round de JavaScript: los medios suben (2.5 → 3, -2.5 → -2), a diferencia de round()."""
    return math.floor(value + 0.5)


class SignalTracker:
    """Lleva el historial de un tueste y entrega las señales de cada segundo."""

    def __init__(self, settings: SignalSettings):
        self.settings = settings
        # La ventana guarda una muestra de más: la de referencia, igual que RoastMetrics.
        self.window: deque[float] = deque(maxlen=settings.rise_window_seconds + 1)
        self.previous: RoastSample | None = None
        self.two_before: RoastSample | None = None
        self.phase_seconds = 0
        self.stagnation = 0
        self.above_ceiling = 0
        self.peak: float | None = None
        self.lowest_after_charge: float | None = None
        self.turning_point = False
        self.first_crack_roast_seconds: int | None = None

    def update(self, sample: RoastSample) -> RoastSignals:
        self.window.append(sample.temperature)
        same_phase = self.previous is not None and self.previous.phase is sample.phase
        self.phase_seconds = self.phase_seconds + 1 if same_phase else 1
        charged = sample.phase in CHARGED_PHASES
        if charged:
            self._track_charged(sample)
        self.stagnation = self._next_stagnation(sample)
        if sample.first_crack_reached and self.first_crack_roast_seconds is None:
            self.first_crack_roast_seconds = sample.roast_seconds

        signals = RoastSignals(
            temperature=sample.temperature,
            rise_per_minute=self._rise_per_minute(),
            stagnation_seconds=self.stagnation,
            seconds_above_ceiling=self.above_ceiling if charged else 0,
            development_ratio=self._development_ratio(sample.roast_seconds),
            roast_seconds=sample.roast_seconds,
            moisture_pct=sample.moisture_pct,
            phase=sample.phase,
            first_crack_reached=sample.first_crack_reached,
            seconds_in_phase=self.phase_seconds,
            peak_temperature=self.peak,
            turning_point_reached=self.turning_point,
        )
        self.two_before, self.previous = self.previous, sample
        return signals

    def _track_charged(self, sample: RoastSample) -> None:
        temperature = sample.temperature
        self.peak = temperature if self.peak is None else max(self.peak, temperature)
        ceiling = self.settings.burn_ceiling_temperature
        self.above_ceiling = self.above_ceiling + 1 if temperature > ceiling else 0
        if self.lowest_after_charge is None or temperature < self.lowest_after_charge:
            self.lowest_after_charge = temperature
        rise = temperature - self.lowest_after_charge
        if rise > 0 and rise >= self.settings.turning_point_min_rise:
            self.turning_point = True

    def _rise_per_minute(self) -> int:
        elapsed = len(self.window) - 1
        if elapsed <= 0:
            return 0
        return js_round((self.window[-1] - self.window[0]) / elapsed * 60)

    def _next_stagnation(self, sample: RoastSample) -> int:
        # MaillardStagnationMonitor: solo en ROASTING y antes del primer crack (con el valor del tick
        # anterior), y compara con la temperatura de dos ticks atrás, como hace el simulador.
        first_crack_before = self.previous is not None and self.previous.first_crack_reached
        if sample.phase is not RoastPhase.ROASTING or first_crack_before:
            return self.stagnation
        start, end = self.settings.maillard_start_temperature, self.settings.maillard_end_temperature
        if not start <= sample.temperature <= end:
            return self.stagnation
        reference = self.two_before or self.previous or sample
        return self.stagnation + 1 if sample.temperature <= reference.temperature else 0

    def _development_ratio(self, roast_seconds: int) -> float:
        if self.first_crack_roast_seconds is None or roast_seconds <= 0:
            return 0.0
        return (roast_seconds - self.first_crack_roast_seconds) / roast_seconds
