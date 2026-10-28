import base64

from app.voice.contracts import (
    AudioResponse,
    CitationResponse,
    LatencyResponse,
    ProvidersResponse,
    VoiceAnswerResponse,
)
from app.voice.service import VoiceAnswer

AUDIO_FORMAT = "wav"


def to_response(answer: VoiceAnswer) -> VoiceAnswerResponse:
    audio = answer.audio
    steps = answer.step_millis
    return VoiceAnswerResponse(
        status=answer.status,
        question=answer.question,
        answer=answer.text,
        citations=[CitationResponse(file_name=c.file_name, page_number=c.page_number, title=c.title)
                   for c in answer.citations],
        audio=AudioResponse(format=AUDIO_FORMAT, sample_rate_hz=audio.sample_rate_hz,
                            base64=base64.b64encode(audio.wav).decode("ascii")) if audio else None,
        providers=ProvidersResponse(transcription=answer.transcription_provider, answer=answer.answer_provider,
                                    speech=audio.provider if audio else None),
        latency=LatencyResponse(transcription_millis=steps.get("transcription", 0),
                                retrieval_millis=steps.get("retrieval", 0), answer_millis=steps.get("answer", 0),
                                speech_millis=steps.get("speech", 0), total_millis=answer.total_millis),
    )
