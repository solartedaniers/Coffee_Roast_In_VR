import importlib.util
import sys

from app.config import SERVICE_ROOT
from app.voice.contracts import RoastState, VoiceQuestionContext

spec = importlib.util.spec_from_file_location("voice_probe", SERVICE_ROOT / "scripts" / "voice_probe.py")
voice_probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(voice_probe)


def args_for(*extra):
    sys.argv = ["voice_probe.py", "q.wav", *extra]
    return voice_probe.parse_args()


def test_sends_a_context_the_ia_service_accepts():
    context = voice_probe.context_for(args_for("--heat", "60", "--first-crack-at", "400"))

    parsed = VoiceQuestionContext.model_validate(context)
    assert parsed.roast_state.heat_level_percent == 60
    assert parsed.roast_state.first_crack_reached is True


def test_leaves_user_and_level_to_the_backend():
    context = voice_probe.context_for(args_for("--backend", "http://127.0.0.1:8081"))

    assert "userId" not in context and "knowledgeLevel" not in context
    RoastState.model_validate(context["roastState"])
