import pytest

from app.evaluation.justification_guard import JustificationGuard, fact_names
from tests.test_evaluation_prompt import RUBRIC

GUARD = JustificationGuard(RUBRIC)
DRYING_FACTS = {"inBandPercent": 0, "wrongDirectionChanges": 1,
                "outOfBandEpisodes": [{"kind": "FAST", "startSecond": 35, "correctionSeconds": None}]}


def review(text, facts=None):
    return GUARD.review(text, facts)


def test_collects_nested_fact_names_and_internal_values():
    assert fact_names(DRYING_FACTS) >= {"inBandPercent", "outOfBandEpisodes", "startSecond", "kind", "FAST"}


def test_cleans_internal_names_out_of_a_drying_justification():
    reviewed = review("La temperatura subió muy rápido (inBandPercent = 0) y moviste el gas al lado contrario "
                      "(wrongDirectionChanges = 1).", DRYING_FACTS)

    assert reviewed.text == "La temperatura subió muy rápido y moviste el gas al lado contrario."
    assert reviewed.cleaned is True


def test_the_drying_example_from_the_web_still_says_franja_so_it_is_not_usable():
    reviewed = review("No estuviste en la franja (inBandPercent = 0) y hubo un cambio en dirección equivocada "
                      "(wrongDirectionChanges = 1).", DRYING_FACTS)

    assert reviewed.text is None


@pytest.mark.parametrize("text", [
    "El 100 % del secado estuvo dentro de la franja, cumpliendo el requisito del 80 % para nivel 3.",
    "Mantuviste la temperatura en la Franja recomendada todo el secado.",
    "Te corresponde el nivel 2 porque corregiste el gas tarde en el secado.",
    "Quedaste en niveles 1 y 2 durante el secado por corregir tarde.",
    "Cumpliste el requisito de bajar el gas a tiempo después del crack.",
    "Solo redujiste 2 puntos, por lo que no fue suficiente para el nivel más alto.",
    "Bajaste el gas tarde y por eso quedaste en el nivel más bajo del criterio.",
    "Tu reacción a las alertas alcanzó para el nivel intermedio del tueste.",
])
def test_rejects_rubric_terms_the_player_should_not_see(text):
    assert review(text).text is None


def test_does_not_confuse_the_word_level_without_a_number():
    text = "Bajaste el gas a un nivel más bajo justo en el primer crack."

    assert review(text).text == text


def test_cleans_the_first_crack_example_and_reads_decimal_minutes():
    reviewed = review("El primer crack llegó a los 17.9 min, fuera de la ventana 8‑12 min "
                      "(minutesOutsideFirstCrackWindow = 5.9).", {"minutesOutsideFirstCrackWindow": 5.9})

    assert reviewed.text == "El primer crack llegó a los 17 min 54 s, fuera de la ventana 8‑12 min."


def test_cleans_the_burn_example_and_rounds_the_temperature():
    reviewed = review("No bajaste el gas y el tueste alcanzó 216.24 °C (burnWarningReached = true), "
                      "por encima del aviso.", {"burnWarningReached": True})

    assert reviewed.text == "No bajaste el gas y el tueste alcanzó 216 °C, por encima del aviso."


def test_removes_a_loose_assignment_outside_parentheses():
    reviewed = review("La subida fue pareja inBandPercent = 100 todo el secado.", {"inBandPercent": 100})

    assert reviewed.text == "La subida fue pareja todo el secado."


def test_keeps_plain_text_and_ordinary_parentheses_untouched():
    text = "Bajaste el gas de 30 a 25 justo en el primer crack (a los 10 min) y no llegaste a 215 °C."

    reviewed = review(text, {"firstCrackSecond": 604})

    assert reviewed.text == text and reviewed.cleaned is False


@pytest.mark.parametrize("text", [
    "Tu inBandPercent fue bajo durante todo el secado del café.",
    "El criterio DRYING_GAS quedó bajo porque no corregiste el gas.",
    "Hubo un episodio FAST que no corregiste a tiempo en el secado.",
    "El valor de kind fue rápido y no corregiste el gas en el secado.",
])
def test_rejects_text_that_still_shows_internal_names(text):
    assert review(text, DRYING_FACTS).text is None


def test_rejects_text_that_becomes_too_short_after_cleaning():
    assert review("Bien (inBandPercent = 100).", {"inBandPercent": 100}).text is None


def test_detects_internal_names_without_facts():
    assert GUARD.has_internal_names("Reaccionaste a BURN_WARNING a tiempo.")
    assert GUARD.has_internal_names("Llegaste a peakAfterFirstCrack alto.")
    assert not GUARD.has_internal_names("Bajaste el gas a tiempo después del primer crack.")
