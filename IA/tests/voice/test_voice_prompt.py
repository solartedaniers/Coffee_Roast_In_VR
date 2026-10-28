from app.domain import KnowledgeLevel
from app.rag.citations import Citation, RetrievedPassage
from app.voice.answer.prompt import ConversationTurn, VoiceAnswerPromptBuilder, duration_text
from app.voice.contracts import RoastState

STATE = RoastState(elapsed_seconds=312, bean_temperature=187.44, first_crack_reached=False,
                   target_temperature=205.0, charge_temperature=210.0)
PASSAGES = [RetrievedPassage("El primer crack  ocurre\ncerca de 196 °C.", Citation("a.pdf", 12, "Manual")),
            RetrievedPassage("El desarrollo dura 15-25 %.", Citation("b.pdf", 3, "Guía"))]


def build(state=STATE, level=KnowledgeLevel.BEGINNER, passages=PASSAGES, history=()):
    return VoiceAnswerPromptBuilder(3).build("¿Cuándo llega el primer crack?", state, level, list(passages),
                                             list(history))


def test_numbers_each_source_and_keeps_the_roast_state_as_evidence():
    prompt = build()

    assert "[S1] (Manual, página 12) El primer crack ocurre cerca de 196 °C." in prompt.user
    assert "[S2] (Guía, página 3) El desarrollo dura 15-25 %." in prompt.user
    assert prompt.passages == {"S1": PASSAGES[0], "S2": PASSAGES[1]}
    assert prompt.evidence_ids == {"S1", "S2", "ROAST"}


def test_describes_the_live_roast_state():
    user = build().user

    assert "Tiempo transcurrido: 5 min 12 s." in user
    assert "Temperatura del grano: 187.4 °C (fase: Caramelización (reacción de Maillard))." in user
    assert "Temperatura objetivo: 205.0 °C (faltan 17.6 °C)." in user
    assert "Temperatura de carga: 210.0 °C (fuera del rango recomendado de 180-200 °C)." in user
    assert "Primer crack: todavía no ha ocurrido." in user
    assert "Nivel de calor del quemador: sin dato." in user
    assert "Subida de temperatura: sin dato." in user


def test_uses_heat_rise_rate_and_development_when_unity_sends_them():
    state = RoastState(elapsed_seconds=600, bean_temperature=200, first_crack_reached=True,
                       first_crack_elapsed_seconds=540, heat_level_percent=65, temperature_rise_per_minute=6.25)

    user = build(state=state).user

    assert "Primer crack: ocurrió a los 9 min 0 s; van 1 min 0 s de desarrollo." in user
    assert "Nivel de calor del quemador: 65 %." in user
    assert "Subida de temperatura: 6.2 °C por minuto." in user
    assert "Temperatura objetivo" not in user


def test_asks_for_short_spoken_answers_at_the_player_level():
    system = build(level=KnowledgeLevel.ADVANCED).system

    assert "como máximo 3 frases" in system
    assert "nivel avanzado" in system
    assert "sufficient en false" in system


def test_says_when_there_are_no_sources():
    prompt = build(passages=[])

    assert "(no se encontraron fuentes para esta pregunta)" in prompt.user
    assert prompt.evidence_ids == {"ROAST"}


def test_adds_recent_turns_only_as_context():
    user = build(history=[ConversationTurn("¿Subo el gas?", "Todavía no.")]).user

    assert "CONVERSACIÓN RECIENTE (solo contexto, no es evidencia):\nJugador: ¿Subo el gas?\nProfesor: Todavía no." in user
    assert user.endswith("PREGUNTA DEL JUGADOR: ¿Cuándo llega el primer crack?")
    assert "CONVERSACIÓN" not in build().user


def test_formats_durations_for_listening():
    assert duration_text(59.6) == "1 min 0 s"
    assert duration_text(0) == "0 min 0 s"


def test_answers_as_intermediate_when_the_user_has_not_chosen_a_level():
    assert "nivel intermedio" in build(level=None).system
