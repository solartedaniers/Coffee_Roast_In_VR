import hashlib
import threading
import time
from collections import OrderedDict

from app.contracts import RoastFeedbackRequest


class FeedbackCache:
    """Caché en memoria (LRU con vencimiento) de la retroalimentación ya generada.

    El backend la pide cada vez que se abre una sesión; sin caché cada apertura gastaría cuota
    del proveedor. Se pierde al reiniciar. Con TTL o tamaño 0 queda desactivada.
    """

    def __init__(self, ttl_seconds: int, max_entries: int, clock=time.monotonic):
        self.ttl_seconds = ttl_seconds
        self.max_entries = max_entries
        self.clock = clock
        self.entries: OrderedDict[str, tuple[float, str]] = OrderedDict()
        self.lock = threading.Lock()

    @property
    def enabled(self) -> bool:
        return self.ttl_seconds > 0 and self.max_entries > 0

    @staticmethod
    def key_for(request: RoastFeedbackRequest, configuration: str) -> str:
        """La configuración (proveedores y modelos) entra en la llave: cambiarla no reutiliza respuestas viejas."""
        payload = request.model_dump_json(by_alias=True) + "\n" + configuration
        return hashlib.sha256(payload.encode("utf-8")).hexdigest()

    def get(self, key: str) -> str | None:
        if not self.enabled:
            return None
        with self.lock:
            entry = self.entries.get(key)
            if entry is None:
                return None
            expires_at, value = entry
            if self.clock() >= expires_at:
                del self.entries[key]
                return None
            self.entries.move_to_end(key)
            return value

    def put(self, key: str, value: str) -> None:
        if not self.enabled:
            return
        with self.lock:
            self.entries[key] = (self.clock() + self.ttl_seconds, value)
            self.entries.move_to_end(key)
            while len(self.entries) > self.max_entries:
                self.entries.popitem(last=False)

    def clear(self) -> None:
        with self.lock:
            self.entries.clear()
