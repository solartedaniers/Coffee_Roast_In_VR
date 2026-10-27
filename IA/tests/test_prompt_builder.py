import pytest

from app.contracts import RoastFeedbackRequest
from app.feedback.prompt_builder import FeedbackPromptBuilder
from app.roast_reference import DevelopmentTimeRange, roast_phase_label
from tests.support.recorded_sessions import ALL


@pytest.fixture
def builder(settings) -> FeedbackPromptBuilder:
    return FeedbackPromptBuilder(DevelopmentTimeRange(settings.rag_dtr_optimal_min, settings.rag_dtr_optimal_max))


@pytest.mark.parametrize("recorded", ALL, ids=lambda recorded: recorded.name)
def test_builds_the_same_prompt_as_java(builder, recorded):
    prompt = builder.build(recorded.request, recorded.level, recorded.context)

    assert prompt.encode("utf-8") == recorded.expected_prompt().encode("utf-8")


def test_resolves_roast_phase_from_final_temperature():
    assert roast_phase_label(160.0) == "Secado"
    assert roast_phase_label(200.0) == "Caramelización (reacción de Maillard)"
    assert roast_phase_label(223.9) == "Primer Crack"
    assert roast_phase_label(224.0) == "Segundo Crack"


def test_fails_like_java_when_required_temperatures_are_missing(builder):
    # En Java esto lanzaba NullPointerException y el servicio respondía feedback null.
    with pytest.raises(TypeError):
        builder.build(RoastFeedbackRequest(), None, None)
