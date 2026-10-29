import math
import operator
from collections.abc import Iterable

from app.alerts.catalog import AlertRule, Condition, Operator
from app.alerts.signals import RoastSignals

OPERATIONS = {
    Operator.EQ: operator.eq,
    Operator.NE: operator.ne,
    Operator.LT: operator.lt,
    Operator.LE: operator.le,
    Operator.GT: operator.gt,
    Operator.GE: operator.ge,
}


def holds(conditions: Iterable[Condition], signals: RoastSignals) -> bool:
    """Todas las condiciones a la vez. Una señal sin valor (la humedad si Unity no la manda) no cumple."""
    for condition in conditions:
        value = signals.value(condition.signal)
        if value is None or not OPERATIONS[condition.op](value, condition.value):
            return False
    return True


class RuleState:
    """Estado de una regla durante un tueste: sostén, re-arme, espera entre repeticiones y máximo."""

    def __init__(self, rule: AlertRule):
        self.rule = rule
        self.held_seconds = 0
        self.armed = True
        self.times_fired = 0
        self.last_fired_second: int | None = None

    def update(self, signals: RoastSignals, second: int, seconds_since_last_alert: float = math.inf) -> bool:
        """True si la regla se dispara en este segundo."""
        active = self.is_active(signals)
        self.held_seconds = self.held_seconds + 1 if active else 0
        if not self.armed:
            rearm = self.rule.rearm_when
            self.armed = not active if rearm is None else holds(rearm, signals)
        if not self._ready(second, seconds_since_last_alert):
            return False
        self.armed = False
        self.times_fired += 1
        self.last_fired_second = second
        return True

    def is_active(self, signals: RoastSignals) -> bool:
        return holds(self.rule.when, signals)

    def _ready(self, second: int, seconds_since_last_alert: float) -> bool:
        rule = self.rule
        cooled_down = self.last_fired_second is None or second - self.last_fired_second >= rule.cooldown_seconds
        return (self.armed and cooled_down
                and self.held_seconds >= rule.sustain_seconds
                and self.times_fired < rule.max_repeats
                and seconds_since_last_alert >= rule.requires_quiet_seconds)
