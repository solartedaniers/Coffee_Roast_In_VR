import pytest

from app.voice.audio import WaveLimits, WaveValidator, pcm_to_wave, wave_sample_rate
from app.voice.errors import VoiceErrorCode, VoiceRequestError
from tests.voice.wav_factory import tone_wav

LIMITS = WaveLimits(sample_rate_hz=16_000, min_millis=300, max_seconds=15, max_bytes=600_000)


def error_code(data: bytes, limits: WaveLimits = LIMITS) -> VoiceErrorCode:
    with pytest.raises(VoiceRequestError) as error:
        WaveValidator(limits).validate(data)
    return error.value.code


def test_accepts_pcm16_mono_at_16_khz():
    assert WaveValidator(LIMITS).validate(tone_wav(2.0)) == pytest.approx(2.0)


@pytest.mark.parametrize("data", [
    tone_wav(1.0, rate=44_100), tone_wav(1.0, channels=2), tone_wav(1.0, width=1), b"not a wav", b"RIFF",
], ids=["44khz", "stereo", "8bit", "text", "header-only"])
def test_rejects_other_formats(data):
    assert error_code(data) is VoiceErrorCode.AUDIO_INVALID


def test_rejects_empty_audio():
    assert error_code(b"") is VoiceErrorCode.AUDIO_INVALID


def test_rejects_a_truncated_file():
    assert error_code(tone_wav(1.0)[:-100]) is VoiceErrorCode.AUDIO_INVALID


def test_rejects_audio_too_short_or_too_long():
    assert error_code(tone_wav(0.1)) is VoiceErrorCode.AUDIO_TOO_SHORT
    assert error_code(tone_wav(3.0), WaveLimits(16_000, 300, 2, 600_000)) is VoiceErrorCode.AUDIO_TOO_LONG


def test_rejects_files_over_the_size_limit_before_reading_them():
    assert error_code(tone_wav(1.0), WaveLimits(16_000, 300, 15, 1000)) is VoiceErrorCode.AUDIO_TOO_LARGE


def test_wraps_raw_pcm_as_a_wav_with_its_rate():
    data = pcm_to_wave(b"\x01\x00" * 22_050, 22_050)

    assert wave_sample_rate(data) == 22_050


@pytest.mark.parametrize("pcm", [b"", b"\x01"])
def test_rejects_invalid_pcm(pcm):
    with pytest.raises(ValueError):
        pcm_to_wave(pcm, 22_050)


def test_rejects_synthesized_audio_that_is_not_a_mono_pcm16_wav():
    with pytest.raises(ValueError):
        wave_sample_rate(b"<html>error</html>")
    with pytest.raises(ValueError):
        wave_sample_rate(tone_wav(0.5, channels=2))
