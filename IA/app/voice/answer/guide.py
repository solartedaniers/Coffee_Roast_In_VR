import json
from pathlib import Path


def load_roast_guide(path: Path) -> str:
    """Texto de la guía del tueste de config/roast_guide.json, el mismo que muestra la web."""
    text = json.loads(path.read_text(encoding="utf-8")).get("text")
    if not isinstance(text, str) or not text.strip():
        raise ValueError(f"{path.name} has no guide text")
    return text
