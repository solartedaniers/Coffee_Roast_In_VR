import json
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

from app.voice.audio import pcm_to_wave
from app.voice.speech.base import SynthesisUnavailable, SynthesizedAudio

# En Windows evita que se abra una consola por cada respuesta.
CREATION_FLAGS = subprocess.CREATE_NO_WINDOW if sys.platform == "win32" else 0


@dataclass(frozen=True)
class PiperOptions:
    executable: Path | None
    model: Path | None
    length_scale: float
    timeout_seconds: float
    max_audio_bytes: int


class PiperSynthesizer:
    """Voz local de Piper, el respaldo gratuito: el texto entra por stdin y sale PCM16 por stdout."""

    name = "piper"

    def __init__(self, options: PiperOptions, runner=subprocess.run):
        self.options = options
        self.runner = runner

    def sample_rate(self) -> int:
        model = self.options.model
        try:
            config = json.loads(Path(f"{model}.json").read_text(encoding="utf-8"))
            return int(config["audio"]["sample_rate"])
        except (OSError, ValueError, KeyError, TypeError):
            raise SynthesisUnavailable("Piper voice configuration is missing or invalid") from None

    def command(self) -> list[str]:
        return [str(self.options.executable), "--model", str(self.options.model), "--output_raw",
                "--length_scale", f"{self.options.length_scale:.2f}"]

    def synthesize(self, text: str, time_budget_seconds: float) -> SynthesizedAudio:
        executable, model = self.options.executable, self.options.model
        if not executable or not model or not executable.is_file() or not model.is_file():
            raise SynthesisUnavailable("Piper is not installed as configured")
        rate = self.sample_rate()
        try:
            result = self.runner(self.command(), input=text.encode("utf-8"), capture_output=True,
                                 timeout=min(self.options.timeout_seconds, time_budget_seconds),
                                 cwd=executable.parent, creationflags=CREATION_FLAGS, check=False)
        except (OSError, subprocess.TimeoutExpired) as error:
            raise SynthesisUnavailable(f"Piper did not finish ({type(error).__name__})") from None
        pcm = result.stdout
        if result.returncode != 0 or not pcm or len(pcm) > self.options.max_audio_bytes:
            raise SynthesisUnavailable(f"Piper failed (exit {result.returncode})")
        try:
            return SynthesizedAudio(pcm_to_wave(pcm, rate), rate, self.name)
        except ValueError:
            raise SynthesisUnavailable("Piper returned invalid audio") from None
