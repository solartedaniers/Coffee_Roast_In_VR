"""Lee en voz alta un texto que arma el backend: la nota y la retroalimentación al terminar el tueste.

Usa la misma cadena de voces del asistente (Azure con su contador mensual y Piper de respaldo). Lo último
leído queda en memoria para que pedir otra vez el mismo tueste no gaste caracteres de Azure.
"""
import threading
from collections import OrderedDict

from app.voice.deadline import Deadline
from app.voice.speech.base import SynthesizedAudio
from app.voice.speech.chain import SpeechChain


class SpeechTextRejected(Exception):
    """El texto viene vacío o pasa del largo máximo configurado."""


class TextSpeech:
    def __init__(self, chain: SpeechChain, max_chars: int, cache_max_entries: int, time_budget_seconds: float):
        self.chain = chain
        self.max_chars = max_chars
        self.cache_max_entries = cache_max_entries
        self.time_budget_seconds = time_budget_seconds
        self.recent: OrderedDict[str, SynthesizedAudio] = OrderedDict()
        self.lock = threading.Lock()

    def speak(self, text: str) -> SynthesizedAudio | None:
        """None si ninguna voz pudo; el jugador igual tiene el texto en pantalla."""
        spoken = text.strip()
        if not spoken or len(spoken) > self.max_chars:
            raise SpeechTextRejected(f"The text must have between 1 and {self.max_chars} characters")
        cached = self._cached(spoken)
        if cached is not None:
            return cached
        audio = self.chain.speak(spoken, Deadline(self.time_budget_seconds))
        if audio is not None:
            self._remember(spoken, audio)
        return audio

    def _cached(self, text: str) -> SynthesizedAudio | None:
        with self.lock:
            audio = self.recent.get(text)
            if audio is not None:
                self.recent.move_to_end(text)
            return audio

    def _remember(self, text: str, audio: SynthesizedAudio) -> None:
        if self.cache_max_entries <= 0:
            return
        with self.lock:
            self.recent[text] = audio
            self.recent.move_to_end(text)
            while len(self.recent) > self.cache_max_entries:
                self.recent.popitem(last=False)
