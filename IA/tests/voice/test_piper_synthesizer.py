import json
import subprocess

import pytest

from app.voice.audio import wave_sample_rate
from app.voice.speech.base import SynthesisUnavailable
from app.voice.speech.piper import PiperOptions, PiperSynthesizer


@pytest.fixture
def installed(tmp_path):
    executable = tmp_path / "piper" / "piper.exe"
    executable.parent.mkdir()
    executable.write_bytes(b"")
    model = tmp_path / "voices" / "es_MX-claude-high.onnx"
    model.parent.mkdir()
    model.write_bytes(b"")
    (tmp_path / "voices" / "es_MX-claude-high.onnx.json").write_text(
        json.dumps({"audio": {"sample_rate": 22050}}), encoding="utf-8")
    return executable, model


class FakeRunner:
    def __init__(self, stdout=b"\x01\x00" * 2205, returncode=0, error=None):
        self.stdout = stdout
        self.returncode = returncode
        self.error = error
        self.calls = []

    def __call__(self, command, **kwargs):
        self.calls.append((command, kwargs))
        if self.error:
            raise self.error
        return subprocess.CompletedProcess(command, self.returncode, self.stdout, b"")


def piper(installed, runner, max_audio_bytes=1_000_000) -> PiperSynthesizer:
    executable, model = installed
    return PiperSynthesizer(PiperOptions(executable, model, 1.1, 8, max_audio_bytes), runner=runner)


def test_sends_the_text_on_stdin_and_wraps_the_raw_pcm_as_wav(installed):
    runner = FakeRunner()

    audio = piper(installed, runner).synthesize("Baja el gas.", 5)

    command, kwargs = runner.calls[0]
    assert command[1:] == ["--model", str(installed[1]), "--output_raw", "--length_scale", "1.10"]
    assert kwargs["input"] == "Baja el gas.".encode("utf-8")
    assert kwargs["timeout"] == 5
    assert (audio.provider, audio.sample_rate_hz, wave_sample_rate(audio.wav)) == ("piper", 22050, 22050)


def test_is_unavailable_when_it_is_not_installed(tmp_path):
    runner = FakeRunner()
    options = PiperOptions(tmp_path / "missing.exe", tmp_path / "missing.onnx", 1.0, 8, 1_000_000)

    with pytest.raises(SynthesisUnavailable, match="not installed"):
        PiperSynthesizer(options, runner=runner).synthesize("hola", 5)
    with pytest.raises(SynthesisUnavailable):
        PiperSynthesizer(PiperOptions(None, None, 1.0, 8, 1_000_000), runner=runner).synthesize("hola", 5)
    assert runner.calls == []


@pytest.mark.parametrize("runner", [
    FakeRunner(returncode=1), FakeRunner(stdout=b""), FakeRunner(stdout=b"\x01"),
    FakeRunner(error=subprocess.TimeoutExpired("piper", 5)), FakeRunner(error=OSError("denied")),
], ids=["exit-1", "no-audio", "odd-bytes", "timeout", "os-error"])
def test_reports_piper_failing(installed, runner):
    with pytest.raises(SynthesisUnavailable):
        piper(installed, runner).synthesize("hola", 5)


def test_rejects_audio_over_the_size_limit(installed):
    with pytest.raises(SynthesisUnavailable):
        piper(installed, FakeRunner(stdout=b"\x01\x00" * 1000), max_audio_bytes=100).synthesize("hola", 5)
