import pytest

from app.java_format import fixed, integer, text
from app.domain import RoastingResult

# Valores obtenidos con String.format(Locale.US, ...) en OpenJDK 17.
ONE_DECIMAL = [
    (0.05, "0.1"), (0.15, "0.2"), (0.25, "0.3"), (0.35, "0.4"), (1.45, "1.5"), (2.675, "2.7"),
    (199.95, "200.0"), (200.05, "200.1"), (-0.05, "-0.1"), (-0.0, "-0.0"), (0.0, "0.0"),
    (1e-7, "0.0"), (12345.65, "12345.7"), (228.0 - 205.0, "23.0"), (0.95, "1.0"),
]
NO_DECIMALS = [
    (0.5, "1"), (1.5, "2"), (2.5, "3"), (20.5, "21"), (19.999, "20"), (156 / 780 * 100, "20"),
    (300 / 900 * 100, "33"), (17.5, "18"), (18.5, "19"),
]


@pytest.mark.parametrize(("value", "expected"), ONE_DECIMAL)
def test_formats_one_decimal_like_java(value, expected):
    assert fixed(value, 1) == expected


@pytest.mark.parametrize(("value", "expected"), NO_DECIMALS)
def test_formats_no_decimals_like_java(value, expected):
    assert fixed(value, 0) == expected


def test_formats_nulls_like_java():
    assert fixed(None, 1) == "n"
    assert fixed(None, 0) == ""
    assert integer(None) == "null"
    assert text(None) == "null"


def test_formats_integers_and_enums():
    assert integer(92) == "92"
    assert text(RoastingResult.PERFECT) == "PERFECT"
