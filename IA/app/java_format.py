"""Formato de números igual al de String.format(Locale.US, ...) de Java, para que el prompt no cambie."""
import math
from decimal import ROUND_HALF_UP, Decimal

NULL_TEXT = "null"


def fixed(value: float | None, places: int) -> str:
    """Equivale a "%.<places>f".

    Java redondea mitad hacia arriba sobre la representación decimal corta del número
    (0.15 -> "0.2"), mientras que Python redondea sobre el valor binario (0.15 -> "0.1").
    Con un nulo, Java recorta "null" a la precisión pedida.
    """
    if value is None:
        return NULL_TEXT[:places]
    if math.isnan(value):
        return "NaN"
    if math.isinf(value):
        return "Infinity" if value > 0 else "-Infinity"
    exponent = Decimal(1).scaleb(-places)
    return format(Decimal(repr(float(value))).quantize(exponent, rounding=ROUND_HALF_UP), "f")


def integer(value: int | None) -> str:
    """Equivale a "%d"."""
    return NULL_TEXT if value is None else str(value)


def text(value: object | None) -> str:
    """Equivale a "%s" con enums y cadenas."""
    return NULL_TEXT if value is None else str(value)
