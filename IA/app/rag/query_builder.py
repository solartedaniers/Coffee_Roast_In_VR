"""Texto con el que se busca en pgvector los fragmentos relevantes para una sesión.

Va en español y reutiliza el vocabulario de fases y resultados del prompt para parecerse al
lenguaje de los PDFs de rag-docs/. Debe coincidir con la búsqueda que hacía Java (tests/snapshots).
"""
from app.contracts import RoastFeedbackRequest
from app.domain import KnowledgeLevel
from app.java_format import fixed, integer, text
from app.roast_reference import (
    IDEAL_FINAL_MAX_C,
    IDEAL_FINAL_MIN_C,
    DevelopmentTimeRange,
    charge_out_of_range,
    distance_outside_ideal_final,
    roast_phase_label,
)


class RagQueryBuilder:
    def __init__(self, development_range: DevelopmentTimeRange):
        self.development_range = development_range

    def build(self, session: RoastFeedbackRequest, knowledge_level: KnowledgeLevel | None) -> str:
        level = knowledge_level or KnowledgeLevel.INTERMEDIATE
        final = session.final_temperature
        parts = [
            f"Tueste de café. Resultado: {text(session.result)}. Puntaje: {integer(session.quality_score)}/100. "
            f"Fase alcanzada: {roast_phase_label(final)}. Nivel del usuario: {level.value}."
        ]

        distance = distance_outside_ideal_final(final)
        below_ideal = distance < 0
        above_ideal = distance > 0
        band = f"{fixed(IDEAL_FINAL_MIN_C, 0)}-{fixed(IDEAL_FINAL_MAX_C, 0)}°C"
        if below_ideal:
            parts.append(f"Temperatura final por debajo del rango ideal ({fixed(final, 1)}°C vs {band}, "
                         f"faltaron {fixed(-distance, 1)}°C).")
        elif above_ideal:
            parts.append(f"Temperatura final por encima del rango ideal ({fixed(final, 1)}°C vs {band}, "
                         f"{fixed(distance, 1)}°C de exceso).")

        ratio = self._development_time_ratio(session)
        under_developed = ratio is not None and ratio < self.development_range.optimal_min
        over_developed = ratio is not None and ratio > self.development_range.optimal_max
        if under_developed:
            parts.append("Tiempo de desarrollo corto tras el first crack, riesgo de subdesarrollo.")
        elif over_developed:
            parts.append("Tiempo de desarrollo prolongado tras el first crack, riesgo de sobre-tueste.")

        # Solo cuando coinciden los dos extremos, para no forzar un defecto que no aplica.
        if below_ideal and over_developed:
            parts.append("Posible horneado.")
        elif above_ideal and under_developed:
            parts.append("Posible quemado.")

        charge = session.charge_temperature
        if charge is not None and charge_out_of_range(charge):
            parts.append(f"Temperatura de carga fuera del rango ideal ({fixed(charge, 1)}°C).")

        return " ".join(parts)

    @staticmethod
    def _development_time_ratio(session: RoastFeedbackRequest) -> float | None:
        development_time = session.development_time_seconds
        total_duration = session.total_duration_seconds
        if development_time is None or total_duration is None or total_duration <= 0:
            return None
        return development_time / total_duration
