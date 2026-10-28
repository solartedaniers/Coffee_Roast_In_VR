import logging
from dataclasses import dataclass

from app.voice.deadline import Deadline
from app.voice.errors import VoiceErrorCode, VoiceRequestError
from app.voice.silence import SilencePhraseFilter
from app.voice.transcription.base import NoSpeechDetected, Transcriber, TranscriptionUnavailable

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class Transcript:
    text: str
    provider: str


class TranscriptionChain:
    """Prueba los proveedores en orden. Si uno dice que no hubo habla, no se gasta el respaldo."""

    def __init__(self, transcribers: list[Transcriber], silence: SilencePhraseFilter, max_chars: int):
        self.transcribers = transcribers
        self.silence = silence
        self.max_chars = max_chars

    def transcribe(self, wav: bytes, deadline: Deadline) -> Transcript:
        for transcriber in self.transcribers:
            if deadline.expired:
                log.warning("Voice time budget exhausted before transcribing with %s", transcriber.name)
                break
            try:
                raw = transcriber.transcribe(wav, deadline.remaining())
            except NoSpeechDetected:
                raise VoiceRequestError(VoiceErrorCode.SILENT_AUDIO) from None
            except TranscriptionUnavailable as error:
                log.warning("Transcription provider %s failed: %s", transcriber.name, error)
                continue
            text = " ".join(raw.split())
            if self.silence.is_silence(text):
                raise VoiceRequestError(VoiceErrorCode.SILENT_AUDIO)
            return Transcript(text[:self.max_chars], transcriber.name)
        raise TranscriptionUnavailable("No transcription provider answered")
