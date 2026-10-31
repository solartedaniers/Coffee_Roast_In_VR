import pytest

from app.contracts import RoastFeedbackRequest
from app.domain import RoastingResult
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


@pytest.mark.parametrize("final, expected", [
    (202.0, "Quedó 3.0°C POR DEBAJO del rango ideal de 205-212°C."),
    (205.0, "Quedó dentro del rango ideal de 205-212°C."),
    (207.0, "Quedó dentro del rango ideal de 205-212°C."),
    (212.0, "Quedó dentro del rango ideal de 205-212°C."),
    (214.5, "Quedó 2.5°C POR ENCIMA del rango ideal de 205-212°C."),
])
def test_compares_the_final_temperature_with_the_ideal_band_not_the_target(builder, final, expected):
    # Un tueste según la guía termina en 205-212 °C aunque el objetivo del jugador sea otro.
    session = RoastFeedbackRequest(result=RoastingResult.PERFECT, quality_score=100, target_temperature=200.0,
                                   final_temperature=final, total_duration_seconds=780, development_time_seconds=156)

    prompt = builder.build(session, None, None)

    assert expected in prompt
    assert "objetivo" not in prompt
