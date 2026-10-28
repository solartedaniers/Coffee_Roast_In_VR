"""Evaluador de referencia de las alertas de un tueste. Unity implementa el mismo en C#."""
from dataclasses import dataclass
from enum import StrEnum

from app.alerts.catalog import AlertCatalog, Priority, RoastPhase
from app.alerts.channel import VoiceChannel
from app.alerts.rules import RuleState
from app.alerts.signals import RoastSample, SignalTracker
from app.domain import KnowledgeLevel


class DeliveryMode(StrEnum):
    VOICE = "VOICE"
    # Mientras el jugador graba una pregunta: así Whisper no transcribe la alerta.
    SUBTITLE = "SUBTITLE"


@dataclass(frozen=True)
class AlertDelivery:
    second: int
    roast_seconds: int
    phase: RoastPhase
    alert_id: str
    priority: Priority
    mode: DeliveryMode
    text: str


class AlertEvaluator:
    """Un tueste de un nivel. step() recibe una muestra por segundo simulado, en orden."""

    def __init__(self, catalog: AlertCatalog, level: KnowledgeLevel):
        self.level = level
        self.tracker = SignalTracker(catalog.signals)
        self.states = {rule.id: RuleState(rule) for rule in catalog.rules_for(level)}
        self.channel = VoiceChannel(catalog.channel)
        self.second = -1

    def step(self, sample: RoastSample, recording: bool = False, assistant_busy: bool = False) -> AlertDelivery | None:
        self.second += 1
        signals = self.tracker.update(sample)
        quiet = self.channel.seconds_since_last_delivery(self.second)
        triggered = [state.rule for state in self.states.values() if state.update(signals, self.second, quiet)]
        rule = self.channel.offer(triggered, self.second, lambda pending: self.states[pending.id].is_active(signals),
                                  assistant_busy)
        if rule is None:
            return None
        return AlertDelivery(second=self.second, roast_seconds=sample.roast_seconds, phase=sample.phase,
                             alert_id=rule.id, priority=rule.priority,
                             mode=DeliveryMode.SUBTITLE if recording else DeliveryMode.VOICE,
                             text=rule.phrases[self.level])
