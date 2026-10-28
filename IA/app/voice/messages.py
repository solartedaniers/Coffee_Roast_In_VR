import json
from dataclasses import dataclass
from pathlib import Path

from app.voice.errors import VoiceErrorCode


@dataclass(frozen=True)
class VoiceMessages:
    """Textos que escucha o ve el jugador, leídos de config/voice_messages.json."""

    abstention: str
    unavailable: str
    errors: dict[VoiceErrorCode, str]

    @classmethod
    def from_file(cls, path: Path) -> "VoiceMessages":
        data = json.loads(path.read_text(encoding="utf-8"))
        errors = data.get("errors") or {}
        missing = [code.value for code in VoiceErrorCode if not isinstance(errors.get(code.value), str)]
        texts = [data.get("abstention"), data.get("unavailable")]
        if missing or not all(isinstance(text, str) and text.strip() for text in texts):
            raise ValueError(f"{path.name} is missing voice messages: {missing or 'abstention/unavailable'}")
        return cls(data["abstention"], data["unavailable"], {code: errors[code.value] for code in VoiceErrorCode})

    def error(self, code: VoiceErrorCode) -> str:
        return self.errors[code]
