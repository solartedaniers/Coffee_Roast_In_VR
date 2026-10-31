"""Mensajes para el modelo que responde la pregunta por voz.

Cada evidencia lleva un identificador (S1, S2... para los PDFs y ROAST para el estado del tueste)
y el modelo debe devolver cuáles usó: así se valida que la respuesta tenga respaldo.
"""
from dataclasses import dataclass

from app.domain import KnowledgeLevel
from app.feedback.prompt_builder import LEVEL_LABELS
from app.rag.citations import RetrievedPassage
from app.roast_reference import CHARGE_IDEAL_MAX_C, CHARGE_IDEAL_MIN_C, charge_out_of_range, roast_phase_label
from app.voice.contracts import RoastState

ROAST_SOURCE_ID = "ROAST"
GUIDE_SOURCE_ID = "GUIA"
SOURCE_ID_PREFIX = "S"
SECONDS_PER_MINUTE = 60

SYSTEM_TEMPLATE = (
    "Eres un profesor de tueste de café que acompaña al jugador mientras tuesta en un simulador de "
    "realidad virtual. Tu respuesta se va a escuchar, no a leer: escribe como máximo {max_sentences} frases "
    "cortas en español, en texto plano, sin listas, sin markdown y sin mencionar las fuentes ni sus nombres.\n"
    "Solo puedes afirmar lo que respaldan las FUENTES, el ESTADO DEL TUESTE o la GUÍA DEL SIMULADOR que te doy. "
    "Para aconsejar el gas sigue la GUÍA DEL SIMULADOR según el estado del tueste. La conversación "
    "reciente es solo contexto, no evidencia. No inventes objetos ni pasos.\n"
    "Cifras: solo puedes dar un número (temperatura, porcentaje de gas, tiempo) si aparece tal cual en las "
    "FUENTES que uses, en el ESTADO DEL TUESTE, en la GUÍA DEL SIMULADOR o en la pregunta, y siempre escrito "
    "con dígitos. Si no tienes una cifra con respaldo, da solo la dirección, por ejemplo \"baja un poco el gas\" "
    "o \"espera a que suba la temperatura\". Nunca calcules ni estimes cifras nuevas.\n"
    "Si con eso no alcanza para responder la pregunta, pon sufficient en false y deja answer vacío.\n"
    "En sourceIds pon los identificadores exactos (por ejemplo S1, ROAST o GUIA) de lo que usaste para responder.\n"
    "El jugador es de nivel {level}."
)

NO_SOURCES = "(no se encontraron fuentes para esta pregunta)"
NO_VALUE = "sin dato"


@dataclass(frozen=True)
class ConversationTurn:
    question: str
    answer: str


@dataclass(frozen=True)
class AnswerPrompt:
    system: str
    user: str
    # Identificador de cada fuente del prompt; ROAST no tiene cita porque no es un PDF.
    passages: dict[str, RetrievedPassage]
    question: str = ""
    state_text: str = ""
    guide_text: str = ""

    @property
    def evidence_ids(self) -> set[str]:
        return set(self.passages) | {ROAST_SOURCE_ID} | ({GUIDE_SOURCE_ID} if self.guide_text else set())


def duration_text(seconds: float) -> str:
    minutes, rest = divmod(int(round(seconds)), SECONDS_PER_MINUTE)
    return f"{minutes} min {rest} s"


class VoiceAnswerPromptBuilder:
    def __init__(self, max_sentences: int, guide_text: str = ""):
        self.max_sentences = max_sentences
        self.guide_text = guide_text

    def build(self, question: str, state: RoastState, level: KnowledgeLevel | None,
              passages: list[RetrievedPassage], history: list[ConversationTurn]) -> AnswerPrompt:
        level = level or KnowledgeLevel.INTERMEDIATE
        identified = {f"{SOURCE_ID_PREFIX}{index}": passage for index, passage in enumerate(passages, start=1)}
        state_text = "\n".join(f"- {line}" for line in self._state_lines(state))
        user = "\n\n".join([
            f"ESTADO DEL TUESTE [{ROAST_SOURCE_ID}]:\n" + state_text,
            *([f"GUÍA DEL SIMULADOR [{GUIDE_SOURCE_ID}] (lo que la web le enseña al jugador):\n{self.guide_text}"]
              if self.guide_text else []),
            "FUENTES:\n" + ("\n".join(self._source_line(source_id, passage)
                                      for source_id, passage in identified.items()) or NO_SOURCES),
            *([self._history_block(history)] if history else []),
            f"PREGUNTA DEL JUGADOR: {question}",
        ])
        system = SYSTEM_TEMPLATE.format(max_sentences=self.max_sentences, level=LEVEL_LABELS[level])
        return AnswerPrompt(system, user, identified, question, state_text, self.guide_text)

    @staticmethod
    def _state_lines(state: RoastState) -> list[str]:
        lines = [
            f"Tiempo transcurrido: {duration_text(state.elapsed_seconds)}.",
            f"Temperatura del grano: {state.bean_temperature:.1f} °C "
            f"(fase: {roast_phase_label(state.bean_temperature)}).",
        ]
        # Antes del primer crack el objetivo es el del modo automático, no una meta del secado: con
        # "faltan X °C" el modelo pedía subir el gas en pleno secado, contra la guía.
        if state.target_temperature is not None and state.first_crack_reached:
            gap = state.target_temperature - state.bean_temperature
            where = f"faltan {gap:.1f} °C" if gap > 0 else f"ya la superó por {-gap:.1f} °C" if gap < 0 else "alcanzada"
            lines.append(f"Temperatura objetivo: {state.target_temperature:.1f} °C ({where}).")
        if state.charge_temperature is not None:
            inside = "fuera" if charge_out_of_range(state.charge_temperature) else "dentro"
            lines.append(f"Temperatura de carga: {state.charge_temperature:.1f} °C ({inside} del rango recomendado "
                         f"de {CHARGE_IDEAL_MIN_C:.0f}-{CHARGE_IDEAL_MAX_C:.0f} °C).")
        if not state.first_crack_reached:
            lines.append("Primer crack: todavía no ha ocurrido.")
        elif state.first_crack_elapsed_seconds is None:
            lines.append("Primer crack: ya ocurrió.")
        else:
            development = max(0.0, state.elapsed_seconds - state.first_crack_elapsed_seconds)
            lines.append(f"Primer crack: ocurrió a los {duration_text(state.first_crack_elapsed_seconds)}; "
                         f"van {duration_text(development)} de desarrollo.")
        lines.append("Nivel de calor del quemador: " + (
            f"{state.heat_level_percent:.0f} %." if state.heat_level_percent is not None else f"{NO_VALUE}."))
        lines.append("Subida de temperatura: " + (
            f"{state.temperature_rise_per_minute:.1f} °C por minuto." if state.temperature_rise_per_minute is not None
            else f"{NO_VALUE}."))
        return lines

    @staticmethod
    def _source_line(source_id: str, passage: RetrievedPassage) -> str:
        citation = passage.citation
        origin = f" ({citation.title}, página {citation.page_number})" if citation else ""
        return f"[{source_id}]{origin} {' '.join(passage.text.split())}"

    @staticmethod
    def _history_block(history: list[ConversationTurn]) -> str:
        turns = "\n".join(f"Jugador: {turn.question}\nProfesor: {turn.answer}" for turn in history)
        return "CONVERSACIÓN RECIENTE (solo contexto, no es evidencia):\n" + turns
