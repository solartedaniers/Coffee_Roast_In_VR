"""Convierte una sesión de tueste ya evaluada en el prompt del modelo.

El texto está en español a propósito: es lo que hace que el modelo responda en español. Debe
coincidir byte a byte con el que armaba el servicio en Java (ver tests/snapshots).
"""
from app.contracts import RoastFeedbackRequest
from app.domain import KnowledgeLevel
from app.java_format import fixed, integer, text
from app.roast_reference import (
    CHARGE_IDEAL_MAX_C,
    CHARGE_IDEAL_MIN_C,
    DevelopmentTimeRange,
    charge_out_of_range,
    roast_phase_label,
)

# Un solo bloque, sin mensaje de sistema aparte: con phi3 separar instrucciones y datos lo hacía
# inventar secciones tipo tutorial. "Retroalimentación:" al final hace que continúe directo.
PROMPT_TEMPLATE = (
    "Datos de una sesión de tueste de café en un simulador por computadora "
    "(no hay objetos físicos: solo estos números).\n"
    "Resultado: {result}. Puntaje: {score}/100.\n"
    "Temperatura de carga: {charge}.\n"
    "Temperatura objetivo: {target}°C. Temperatura final: {final}°C. {delta}\n"
    "Fase del tueste alcanzada al finalizar: {phase}.\n"
    "Duración total: {duration} segundos. First crack alcanzado: {first_crack}.\n"
    "{development}\n"
    "Nivel del usuario: {level}.\n"
    "Escribe 2 o 3 frases en español, en texto plano, explicando qué salió "
    "bien o mal según esos datos y una sugerencia concreta para el próximo "
    "tueste. No inventes objetos, herramientas ni escenas que no estén en "
    "los datos. No repitas estas instrucciones. No uses markdown ni encabezados.\n"
    "{reminder}\n"
    "{context}\n"
    "Recuerda el resultado real de esta sesión: {result} (puntaje {score}/100). Tu "
    "sugerencia debe ser consistente con ese resultado, no contradecirlo.\n"
    "\n"
    "Retroalimentación:"
)

YES = "sí"
NO = "no"
NOT_AVAILABLE = "N/A"
BELOW_TARGET_LABEL = "POR DEBAJO"
ABOVE_TARGET_LABEL = "POR ENCIMA"
DEVELOPMENT_TIME_SHORT_LABEL = "CORTO"
DEVELOPMENT_TIME_LONG_LABEL = "PROLONGADO"
DEVELOPMENT_TIME_OPTIMAL_LABEL = "dentro del rango óptimo"
CONTEXT_HEADER = (
    "Contexto de referencia de material académico (úsalo solo si es relevante, no lo cites textualmente):"
)

LEVEL_LABELS = {
    KnowledgeLevel.BEGINNER: "principiante, usa vocabulario simple y explica cualquier término técnico que uses",
    KnowledgeLevel.INTERMEDIATE: "intermedio, usa vocabulario técnico moderado sin explicaciones básicas",
    KnowledgeLevel.ADVANCED: "avanzado, usa vocabulario técnico de tostador experto y sé directo",
}

# Se repite justo antes de "Retroalimentación:" porque una sola mención del nivel entre los
# datos no bastaba para que el modelo cambiara el registro (verificado con phi4-mini).
VOCABULARY_REMINDERS = {
    KnowledgeLevel.BEGINNER: (
        'Recuerda: nivel principiante. Usa palabras simples como "tueste", "tiempo" y "temperatura"; '
        'evita o explica términos como "ratio de desarrollo", "Maillard" o "DTR".'
    ),
    KnowledgeLevel.INTERMEDIATE: (
        'Recuerda: nivel intermedio. Puedes usar términos como "first crack" o "tiempo de desarrollo" '
        'sin explicarlos, pero evita jerga muy especializada como "DTR" o "reacción de Maillard".'
    ),
    KnowledgeLevel.ADVANCED: (
        'Recuerda: nivel avanzado. Usa términos técnicos como "DTR", "ratio de desarrollo" o '
        '"reacción de Maillard" libremente, sin explicarlos, y sé directo.'
    ),
}


class FeedbackPromptBuilder:
    def __init__(self, development_range: DevelopmentTimeRange):
        self.development_range = development_range

    def build(
        self,
        session: RoastFeedbackRequest,
        knowledge_level: KnowledgeLevel | None,
        retrieved_context: list[str] | None,
    ) -> str:
        level = knowledge_level or KnowledgeLevel.INTERMEDIATE
        charge = (self._charge_temperature_text(session.charge_temperature)
                  if session.charge_temperature is not None else NOT_AVAILABLE)
        return PROMPT_TEMPLATE.format(
            result=text(session.result),
            score=integer(session.quality_score),
            charge=charge,
            target=fixed(session.target_temperature, 1),
            final=fixed(session.final_temperature, 1),
            delta=self._target_vs_final_delta_text(session),
            phase=roast_phase_label(session.final_temperature),
            duration=integer(session.total_duration_seconds),
            first_crack=YES if session.first_crack_reached is True else NO,
            development=self._development_time_text(session),
            level=LEVEL_LABELS[level],
            reminder=VOCABULARY_REMINDERS[level],
            context=self._context_block(retrieved_context),
        )

    @staticmethod
    def _target_vs_final_delta_text(session: RoastFeedbackRequest) -> str:
        # La dirección y la magnitud van ya calculadas: el modelo llegó a inventar el objetivo.
        delta = session.final_temperature - session.target_temperature
        if delta == 0:
            return "La temperatura final coincidió exactamente con el objetivo."
        direction = BELOW_TARGET_LABEL if delta < 0 else ABOVE_TARGET_LABEL
        return f"Quedó {fixed(abs(delta), 1)}°C {direction} del objetivo."

    def _development_time_text(self, session: RoastFeedbackRequest) -> str:
        development_time = session.development_time_seconds
        if development_time is None:
            return f"Tiempo de desarrollo tras el first crack: {NOT_AVAILABLE}."
        total_duration = session.total_duration_seconds
        ratio = DevelopmentTimeRange.java_ratio(development_time, total_duration)
        bounds = (f"{fixed(self.development_range.optimal_min * 100, 0)}%"
                  f"-{fixed(self.development_range.optimal_max * 100, 0)}%")
        # Se clasifica explícitamente porque el modelo confundía un DTR alto con uno corto.
        if ratio < self.development_range.optimal_min:
            classification = (f"esto es {DEVELOPMENT_TIME_SHORT_LABEL}, no prolongado, "
                              f"respecto al rango óptimo de {bounds}")
        elif ratio > self.development_range.optimal_max:
            classification = (f"esto es {DEVELOPMENT_TIME_LONG_LABEL}, no corto, "
                              f"respecto al rango óptimo de {bounds}")
        else:
            classification = DEVELOPMENT_TIME_OPTIMAL_LABEL
        return (f"Tiempo de desarrollo tras el first crack: {integer(development_time)} segundos de "
                f"{integer(total_duration)} segundos totales ({fixed(ratio * 100, 0)}% del tueste) "
                f"— {classification}.")

    @staticmethod
    def _charge_temperature_text(charge_temperature: float) -> str:
        # Siempre se dice si está dentro o fuera del rango: el silencio hacía que el modelo
        # inventara una relación entre la carga y el objetivo.
        where = "fuera" if charge_out_of_range(charge_temperature) else "dentro"
        return (f"{fixed(charge_temperature, 1)}°C ({where} del rango recomendado de "
                f"{fixed(CHARGE_IDEAL_MIN_C, 0)}-{fixed(CHARGE_IDEAL_MAX_C, 0)}°C)")

    @staticmethod
    def _context_block(retrieved_context: list[str] | None) -> str:
        if not retrieved_context:
            return ""
        return CONTEXT_HEADER + "".join(f"\n- {chunk}" for chunk in retrieved_context)
