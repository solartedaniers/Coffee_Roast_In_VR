"""Descarta las transcripciones que Whisper inventa con el silencio (patrón de Musiyo API).

Whisper aprendió de videos y subtítulos, así que ante silencio o ruido escribe despedidas como
"gracias por ver el video". Una transcripción que solo contiene una de esas frases no es una pregunta.
"""
import json
import re
import unicodedata
from pathlib import Path


def plain_text(text: str) -> str:
    """Minúsculas, sin tildes ni puntuación y con un solo espacio entre palabras."""
    plain = unicodedata.normalize("NFKD", text.lower())
    plain = "".join(char for char in plain if not unicodedata.combining(char))
    return " ".join(re.sub(r"[^a-z0-9 ]+", " ", plain).split())


class SilencePhraseFilter:
    def __init__(self, patterns: list[str]):
        self.pattern = re.compile("(" + "|".join(f"(?:{pattern})" for pattern in patterns) + r")( gracias)?")

    @classmethod
    def from_file(cls, path: Path) -> "SilencePhraseFilter":
        patterns = json.loads(path.read_text(encoding="utf-8")).get("patterns")
        if not isinstance(patterns, list) or not patterns or not all(isinstance(p, str) and p for p in patterns):
            raise ValueError(f"{path.name} must list the silence patterns")
        return cls(patterns)

    def is_silence(self, transcript: str) -> bool:
        plain = plain_text(transcript)
        return not plain or bool(self.pattern.fullmatch(plain))
