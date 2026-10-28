import logging

from app.voice.deadline import Deadline
from app.voice.speech.base import SynthesisUnavailable, SynthesizedAudio, Synthesizer
from app.voice.speech.quota import CharacterQuota

log = logging.getLogger(__name__)


class SpeechChain:
    """Prueba las voces en orden. Una voz con cuota (Azure) solo se usa si caben los caracteres del mes."""

    def __init__(self, synthesizers: list[Synthesizer], quotas: dict[str, CharacterQuota]):
        self.synthesizers = synthesizers
        self.quotas = quotas

    def speak(self, text: str, deadline: Deadline) -> SynthesizedAudio | None:
        """None si ninguna voz pudo: el jugador igual recibe el texto."""
        for synthesizer in self.synthesizers:
            if deadline.expired:
                log.warning("Voice time budget exhausted before synthesizing with %s", synthesizer.name)
                break
            quota = self.quotas.get(synthesizer.name)
            if quota is not None and not self._reserve(quota, synthesizer.name, len(text)):
                continue
            try:
                return synthesizer.synthesize(text, deadline.remaining())
            except SynthesisUnavailable as error:
                log.warning("Speech provider %s failed: %s", synthesizer.name, error)
                if quota is not None:
                    self._release(quota, synthesizer.name, len(text))
        return None

    @staticmethod
    def _reserve(quota: CharacterQuota, name: str, characters: int) -> bool:
        try:
            if quota.try_reserve(characters):
                return True
            log.info("Monthly character limit reached for %s, using the next voice", name)
        except Exception as error:
            # Sin poder contar no se arriesga la capa gratuita: se pasa a la siguiente voz.
            log.warning("Could not check the %s character quota: %s", name, error)
        return False

    @staticmethod
    def _release(quota: CharacterQuota, name: str, characters: int) -> None:
        try:
            quota.release(characters)
        except Exception as error:
            log.warning("Could not give back %s characters to the %s quota: %s", characters, name, error)
