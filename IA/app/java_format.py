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


# Caracteres que String.strip() de Java considera espacio. A diferencia de Python, Java no
# quita los espacios de no separación (U+00A0, U+2007, U+202F) ni U+0085.
_JAVA_NON_WHITESPACE = {chr(0x00A0), chr(0x2007), chr(0x202F), chr(0x0085)}


def java_strip(value: str) -> str:
    start, end = 0, len(value)
    while start < end and value[start].isspace() and value[start] not in _JAVA_NON_WHITESPACE:
        start += 1
    while end > start and value[end - 1].isspace() and value[end - 1] not in _JAVA_NON_WHITESPACE:
        end -= 1
    return value[start:end]
