import time


class Deadline:
    """Tiempo que le queda a una pregunta por voz; cada paso usa solo lo que sobra."""

    def __init__(self, total_seconds: float, clock=time.monotonic):
        self.clock = clock
        self.ends_at = clock() + total_seconds

    def remaining(self) -> float:
        return max(0.0, self.ends_at - self.clock())

    @property
    def expired(self) -> bool:
        return self.remaining() <= 0
