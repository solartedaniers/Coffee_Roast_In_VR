import math
from collections.abc import Callable, Iterable
from dataclasses import dataclass

from app.alerts.catalog import AlertRule, ChannelPolicy, Priority


@dataclass(frozen=True)
class PendingAlert:
    rule: AlertRule
    queued_at: int


class VoiceChannel:
    """Un solo canal de voz: decide qué alerta suena en cada segundo y cuáles esperan en la cola."""

    def __init__(self, policy: ChannelPolicy):
        self.policy = policy
        self.queue: list[PendingAlert] = []
        self.last_delivery_second: int | None = None

    def seconds_since_last_delivery(self, second: int) -> float:
        return math.inf if self.last_delivery_second is None else second - self.last_delivery_second

    def offer(self, triggered: Iterable[AlertRule], second: int, is_active: Callable[[AlertRule], bool],
              assistant_busy: bool = False) -> AlertRule | None:
        """La alerta que se entrega en este segundo, o None. Como máximo una por segundo."""
        waiting = [pending for pending in self.queue
                   if second - pending.queued_at <= self.policy.queue_max_wait_seconds and is_active(pending.rule)]
        candidates = waiting + [PendingAlert(rule, second) for rule in triggered]
        candidates.sort(key=lambda pending: (pending.rule.priority.rank, pending.queued_at))
        chosen = candidates[0] if candidates and self._can_deliver(candidates[0].rule.priority, second,
                                                                   assistant_busy) else None
        self.queue = [pending for pending in candidates
                      if pending is not chosen and pending.rule.priority not in self.policy.unqueued_priorities]
        if chosen is None:
            return None
        self.last_delivery_second = second
        return chosen.rule

    def _can_deliver(self, priority: Priority, second: int, assistant_busy: bool) -> bool:
        if priority in self.policy.urgent_priorities:
            return True
        return not assistant_busy and self.seconds_since_last_delivery(second) >= self.policy.min_gap_seconds
