from app.voice.answer.figures import SupportedFigures, numbers_in


def test_reads_integers_and_decimals_with_point_or_comma():
    assert numbers_in("Sube 1,5 °C cada 30 s, unos 14.0 por minuto.") == [1.5, 30.0, 14.0]


def test_ignores_digits_inside_source_ids():
    assert numbers_in("Según [S1] y ROAST.") == []


def test_accepts_a_rounded_figure_within_the_tolerance():
    assert SupportedFigures(0.5).keep_supported("El grano va en 198 °C.", "198.4 °C") == "El grano va en 198 °C."


def test_drops_a_figure_outside_the_tolerance():
    assert SupportedFigures(0.5).keep_supported("Va en 199 °C. Sigue así.", "198.4 °C") == "Sigue así."
