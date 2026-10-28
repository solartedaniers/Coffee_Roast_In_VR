import pytest
from pydantic import ValidationError

from app.domain import KnowledgeLevel
from app.voice.contracts import RoastState, VoiceQuestionContext

STATE = {"elapsedSeconds": 312.5, "beanTemperature": 187.4, "firstCrackReached": False,
         "targetTemperature": 205.0, "chargeTemperature": 190.0, "firstCrackElapsedSeconds": None}
CONTEXT = {"userId": 7, "knowledgeLevel": "BEGINNER", "roastAttemptId": "8a7c6f0e-2a55-4c1e-9d43-0b6f1f3d2e10",
           "roastState": STATE}


def test_reads_the_context_sent_by_the_backend():
    context = VoiceQuestionContext.model_validate(CONTEXT)

    assert context.user_id == 7
    assert context.knowledge_level is KnowledgeLevel.BEGINNER
    assert context.roast_state.bean_temperature == 187.4
    assert context.roast_state.heat_level_percent is None
    assert context.roast_state.temperature_rise_per_minute is None


def test_accepts_the_optional_heat_and_rise_rate():
    state = RoastState.model_validate(STATE | {"heatLevelPercent": 70, "temperatureRisePerMinute": -2.5})

    assert (state.heat_level_percent, state.temperature_rise_per_minute) == (70, -2.5)


@pytest.mark.parametrize("change", [
    {"elapsedSeconds": -1}, {"heatLevelPercent": 101}, {"beanTemperature": float("nan")},
    {"firstCrackReached": None},
])
def test_rejects_an_impossible_roast_state(change):
    with pytest.raises(ValidationError):
        RoastState.model_validate(STATE | change)


@pytest.mark.parametrize("change", [{"userId": 0}, {"knowledgeLevel": "EXPERT"}, {"roastAttemptId": "abc"}])
def test_rejects_an_invalid_context(change):
    with pytest.raises(ValidationError):
        VoiceQuestionContext.model_validate(CONTEXT | change)


def test_accepts_a_user_without_a_chosen_level():
    context = VoiceQuestionContext.model_validate(CONTEXT | {"knowledgeLevel": None})

    assert context.knowledge_level is None
