"""Límites por usuario de la pregunta por voz. Viven en memoria: el servicio corre en un solo proceso."""
import threading
import time
from collections import defaultdict, deque
from collections.abc import Iterator
from contextlib import contextmanager

from app.voice.errors import VoiceErrorCode, VoiceRequestError

WINDOW_SECONDS = 60


class UserRequestLimiter:
    def __init__(self, max_concurrent: int, max_per_minute: int, clock=time.monotonic):
        self.max_concurrent = max_concurrent
        self.max_per_minute = max_per_minute
        self.clock = clock
        self.lock = threading.Lock()
        self.active: dict[int, int] = defaultdict(int)
        self.recent: dict[int, deque[float]] = defaultdict(deque)

    @contextmanager
    def admit(self, user_id: int) -> Iterator[None]:
        """Deja pasar la pregunta o lanza ASSISTANT_BUSY / RATE_LIMITED sin contarla."""
        with self.lock:
            if self.active[user_id] >= self.max_concurrent:
                raise VoiceRequestError(VoiceErrorCode.ASSISTANT_BUSY)
            now = self.clock()
            window = self.recent[user_id]
            while window and window[0] <= now - WINDOW_SECONDS:
                window.popleft()
            if len(window) >= self.max_per_minute:
                raise VoiceRequestError(VoiceErrorCode.RATE_LIMITED)
            window.append(now)
            self.active[user_id] += 1
        try:
            yield
        finally:
            with self.lock:
                self.active[user_id] -= 1
                if not self.active[user_id]:
                    del self.active[user_id]
