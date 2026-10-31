"""Rangos de referencia del tueste que comparten el prompt y la búsqueda del RAG.

Son los mismos valores de RoastConstants.js en el frontend (no hay constantes compartidas entre
servicios). Solo describen la sesión al modelo; no afectan el puntaje ni la fase guardada.
"""
from dataclasses import dataclass

CHARGE_IDEAL_MIN_C = 180.0
CHARGE_IDEAL_MAX_C = 200.0

DRYING_PHASE_END_C = 160.0
MAILLARD_PHASE_END_C = 200.0
SECOND_CRACK_PHASE_START_C = 224.0

# Franja de temperatura final sin castigo del puntaje perfecto (IDEAL_FINAL_TEMP_MIN_C/MAX_C).
IDEAL_FINAL_MIN_C = 205.0
IDEAL_FINAL_MAX_C = 212.0
# Franja de duración sin castigo (IDEAL_TOTAL_ROAST_MIN_MINUTES/MAX_MINUTES), en segundos.
IDEAL_TOTAL_MIN_SECONDS = 600
IDEAL_TOTAL_MAX_SECONDS = 900

DRYING_PHASE_LABEL = "Secado"
MAILLARD_PHASE_LABEL = "Caramelización (reacción de Maillard)"
FIRST_CRACK_PHASE_LABEL = "Primer Crack"
SECOND_CRACK_PHASE_LABEL = "Segundo Crack"


def roast_phase_label(final_temperature: float) -> str:
    """Fase alcanzada según la temperatura final; los estados intermedios del frontend
    (DARK) quedan dentro de "Primer Crack" hasta el segundo crack."""
    if final_temperature <= DRYING_PHASE_END_C:
        return DRYING_PHASE_LABEL
    if final_temperature <= MAILLARD_PHASE_END_C:
        return MAILLARD_PHASE_LABEL
    if final_temperature < SECOND_CRACK_PHASE_START_C:
        return FIRST_CRACK_PHASE_LABEL
    return SECOND_CRACK_PHASE_LABEL


def distance_outside_ideal_final(final_temperature: float) -> float:
    """Negativa si quedó por debajo de la franja ideal, positiva si quedó por encima y 0 dentro."""
    if final_temperature < IDEAL_FINAL_MIN_C:
        return final_temperature - IDEAL_FINAL_MIN_C
    if final_temperature > IDEAL_FINAL_MAX_C:
        return final_temperature - IDEAL_FINAL_MAX_C
    return 0.0


def charge_out_of_range(charge_temperature: float) -> bool:
    return charge_temperature < CHARGE_IDEAL_MIN_C or charge_temperature > CHARGE_IDEAL_MAX_C


@dataclass(frozen=True)
class DevelopmentTimeRange:
    """Rango óptimo del Development Time Ratio (tiempo de desarrollo / duración total)."""

    optimal_min: float
    optimal_max: float

    @staticmethod
    def java_ratio(development_time_seconds: int, total_duration_seconds: int) -> float:
        # Java divide entre double: con 0 da infinito o NaN en vez de fallar.
        if total_duration_seconds == 0:
            return float("nan") if development_time_seconds == 0 else float("inf")
        return development_time_seconds / total_duration_seconds
