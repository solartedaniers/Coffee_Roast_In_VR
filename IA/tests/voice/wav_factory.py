import io
import math
import struct
import wave


def tone_wav(seconds: float = 1.0, rate: int = 16_000, channels: int = 1, width: int = 2) -> bytes:
    frames = int(seconds * rate)
    output = io.BytesIO()
    with wave.open(output, "wb") as audio:
        audio.setnchannels(channels)
        audio.setsampwidth(width)
        audio.setframerate(rate)
        if width == 2:
            samples = (int(8000 * math.sin(2 * math.pi * 440 * i / rate)) for i in range(frames))
            audio.writeframes(b"".join(struct.pack("<h", s) * channels for s in samples))
        else:
            audio.writeframes(b"\x80" * frames * channels * width)
    return output.getvalue()
