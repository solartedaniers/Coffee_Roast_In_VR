"""Audio guardado de las frases fijas (no disponible y abstención).

Se generan una vez y se guardan en memoria y en disco: si Groq o Internet fallan el jugador igual
escucha una respuesta, y la abstención no gasta caracteres de Azure cada vez que se repite.
"""
import hashlib
import logging
import threading
from pathlib import Path

from app.voice.audio import wave_sample_rate
from app.voice.deadline import Deadline
from app.voice.speech.base import SynthesizedAudio
from app.voice.speech.chain import SpeechChain

log = logging.getLogger(__name__)
SEPARATOR = "|"


class PhraseAudioCache:
    def __init__(self, chain: SpeechChain, cache_dir: Path, voice_signature: str):
        self.chain = chain
        self.cache_dir = cache_dir
        # Cambiar de voz o de formato invalida lo guardado.
        self.voice_signature = voice_signature
        self.memory: dict[str, SynthesizedAudio] = {}
        self.lock = threading.Lock()

    def key(self, text: str) -> str:
        return hashlib.sha256(f"{self.voice_signature}{SEPARATOR}{text}".encode("utf-8")).hexdigest()

    def get(self, text: str, deadline: Deadline) -> SynthesizedAudio | None:
        key = self.key(text)
        with self.lock:
            if key in self.memory:
                return self.memory[key]
        audio = self._read(key) or self._synthesize(key, text, deadline)
        if audio is not None:
            with self.lock:
                self.memory[key] = audio
        return audio

    def warm(self, texts: list[str], seconds: float) -> None:
        for text in texts:
            self.get(text, Deadline(seconds))

    def _read(self, key: str) -> SynthesizedAudio | None:
        for path in self.cache_dir.glob(f"{key}.*.wav"):
            try:
                wav = path.read_bytes()
                return SynthesizedAudio(wav, wave_sample_rate(wav), path.stem.split(".", 1)[1])
            except (OSError, ValueError, IndexError) as error:
                log.warning("Ignoring an unreadable cached phrase %s: %s", path.name, error)
        return None

    def _synthesize(self, key: str, text: str, deadline: Deadline) -> SynthesizedAudio | None:
        audio = self.chain.speak(text, deadline)
        if audio is None:
            return None
        try:
            self.cache_dir.mkdir(parents=True, exist_ok=True)
            (self.cache_dir / f"{key}.{audio.provider}.wav").write_bytes(audio.wav)
        except OSError as error:
            log.warning("Could not store the cached phrase on disk: %s", error)
        return audio
