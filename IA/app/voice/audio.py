"""Validación del WAV que llega de Unity y armado del WAV de salida (adaptado de Musiyo API)."""
import io
import wave
from dataclasses import dataclass

from app.voice.errors import VoiceErrorCode, VoiceRequestError

PCM16_SAMPLE_WIDTH = 2
MONO = 1
MILLIS_PER_SECOND = 1000


@dataclass(frozen=True)
class WaveLimits:
    sample_rate_hz: int
    min_millis: int
    max_seconds: int
    max_bytes: int


class WaveValidator:
    """Solo acepta PCM16 mono sin compresión a la frecuencia configurada, completo y dentro de los límites."""

    def __init__(self, limits: WaveLimits):
        self.limits = limits

    def validate(self, data: bytes) -> float:
        """Devuelve la duración en segundos o lanza VoiceRequestError."""
        if len(data) > self.limits.max_bytes:
            raise VoiceRequestError(VoiceErrorCode.AUDIO_TOO_LARGE)
        if not data:
            raise VoiceRequestError(VoiceErrorCode.AUDIO_INVALID, "Empty audio")
        try:
            with wave.open(io.BytesIO(data)) as audio:
                if (audio.getnchannels() != MONO or audio.getsampwidth() != PCM16_SAMPLE_WIDTH
                        or audio.getcomptype() != "NONE" or audio.getframerate() != self.limits.sample_rate_hz):
                    raise VoiceRequestError(VoiceErrorCode.AUDIO_INVALID, "Expected PCM16 mono WAV")
                frames = audio.getnframes()
                # El encabezado puede decir más cuadros de los que trae el archivo.
                if len(audio.readframes(frames)) != frames * PCM16_SAMPLE_WIDTH:
                    raise VoiceRequestError(VoiceErrorCode.AUDIO_INVALID, "Truncated audio")
        except (wave.Error, EOFError) as error:
            raise VoiceRequestError(VoiceErrorCode.AUDIO_INVALID, "Invalid WAV") from error
        seconds = frames / self.limits.sample_rate_hz
        if seconds * MILLIS_PER_SECOND < self.limits.min_millis:
            raise VoiceRequestError(VoiceErrorCode.AUDIO_TOO_SHORT)
        if seconds > self.limits.max_seconds:
            raise VoiceRequestError(VoiceErrorCode.AUDIO_TOO_LONG)
        return seconds


def pcm_to_wave(pcm: bytes, sample_rate_hz: int) -> bytes:
    if not pcm or len(pcm) % PCM16_SAMPLE_WIDTH:
        raise ValueError("Invalid PCM16 audio")
    output = io.BytesIO()
    with wave.open(output, "wb") as audio:
        audio.setnchannels(MONO)
        audio.setsampwidth(PCM16_SAMPLE_WIDTH)
        audio.setframerate(sample_rate_hz)
        audio.writeframes(pcm)
    return output.getvalue()


def wave_sample_rate(data: bytes) -> int:
    """Frecuencia de un WAV PCM16 mono generado por un proveedor; ValueError si no lo es."""
    try:
        with wave.open(io.BytesIO(data)) as audio:
            if (audio.getnchannels() != MONO or audio.getsampwidth() != PCM16_SAMPLE_WIDTH
                    or audio.getnframes() == 0):
                raise ValueError("Unexpected synthesized audio format")
            return audio.getframerate()
    except (wave.Error, EOFError) as error:
        raise ValueError("Invalid synthesized WAV") from error
