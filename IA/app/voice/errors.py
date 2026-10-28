"""Errores de la pregunta por voz que se le devuelven a Unity con un código estable."""
from enum import StrEnum


class VoiceErrorCode(StrEnum):
    AUDIO_INVALID = "AUDIO_INVALID"
    AUDIO_TOO_SHORT = "AUDIO_TOO_SHORT"
    AUDIO_TOO_LONG = "AUDIO_TOO_LONG"
    AUDIO_TOO_LARGE = "AUDIO_TOO_LARGE"
    SILENT_AUDIO = "SILENT_AUDIO"
    CONTEXT_INVALID = "CONTEXT_INVALID"
    ASSISTANT_BUSY = "ASSISTANT_BUSY"
    RATE_LIMITED = "RATE_LIMITED"


HTTP_STATUS = {
    VoiceErrorCode.AUDIO_INVALID: 400,
    VoiceErrorCode.AUDIO_TOO_SHORT: 400,
    VoiceErrorCode.AUDIO_TOO_LONG: 400,
    VoiceErrorCode.AUDIO_TOO_LARGE: 413,
    VoiceErrorCode.SILENT_AUDIO: 422,
    VoiceErrorCode.CONTEXT_INVALID: 400,
    VoiceErrorCode.ASSISTANT_BUSY: 409,
    VoiceErrorCode.RATE_LIMITED: 429,
}


class VoiceRequestError(Exception):
    """La pregunta no se puede atender por algo del pedido (audio, límites); no es una falla del proveedor."""

    def __init__(self, code: VoiceErrorCode, detail: str = ""):
        super().__init__(detail or code.value)
        self.code = code

    @property
    def http_status(self) -> int:
        return HTTP_STATUS[self.code]
