from datetime import UTC, datetime
from uuid import uuid4

import pytest

from app.rag.citations import Citation
from app.voice.answer.prompt import ConversationTurn
from app.voice.contracts import AnswerStatus
from app.voice.history import VOICE_EXCHANGES_TABLE, VoiceExchange, VoiceExchangeRepository
from app.voice.schema import VoiceSchema
from app.voice.speech.quota import TTS_USAGE_TABLE, MonthlyCharacterQuota

TEST_PROVIDER = "it-azure"
# Ids que no existen en users, para borrar solo lo que crean estos tests.
TEST_USER_ID = 990_001


@pytest.fixture
def voice_schema(database):
    VoiceSchema(database).ensure()
    yield
    with database.transaction() as connection:
        connection.execute(f"DELETE FROM {TTS_USAGE_TABLE} WHERE provider = %s", (TEST_PROVIDER,))
        connection.execute(f"DELETE FROM {VOICE_EXCHANGES_TABLE} WHERE user_id = %s", (TEST_USER_ID,))


def quota(database, limit=100, month=1) -> MonthlyCharacterQuota:
    return MonthlyCharacterQuota(database, TEST_PROVIDER, limit, clock=lambda: datetime(2026, month, 15, tzinfo=UTC))


def test_reserves_until_the_monthly_limit_and_never_past_it(database, voice_schema):
    counter = quota(database)

    assert counter.try_reserve(60) is True
    assert counter.try_reserve(41) is False
    assert counter.try_reserve(40) is True
    assert counter.used() == 100


def test_gives_back_what_azure_did_not_speak(database, voice_schema):
    counter = quota(database)
    counter.try_reserve(70)

    counter.release(30)

    assert counter.used() == 40


def test_starts_again_each_month(database, voice_schema):
    quota(database, month=1).try_reserve(100)

    assert quota(database, month=2).try_reserve(100) is True


def test_creating_the_tables_twice_changes_nothing(database, voice_schema):
    quota(database).try_reserve(10)
    VoiceSchema(database).ensure()

    assert quota(database).used() == 10


def exchange(attempt, question, answer, status=AnswerStatus.ANSWERED, user_id=TEST_USER_ID) -> VoiceExchange:
    return VoiceExchange(user_id, attempt, question, answer, status, [Citation("a.pdf", 2, "Manual")],
                         "groq", "groq", "azure", 1800)


def test_keeps_the_latest_answered_turns_of_the_same_attempt_in_order(database, voice_schema):
    repository = VoiceExchangeRepository(database)
    attempt, other_attempt = uuid4(), uuid4()
    for number in range(1, 4):
        repository.save(exchange(attempt, f"pregunta {number}", f"respuesta {number}"))
    repository.save(exchange(attempt, "sin evidencia", "No tengo esa información.", AnswerStatus.ABSTAINED))
    repository.save(exchange(other_attempt, "otro tueste", "otra respuesta"))

    turns = repository.recent_answered(TEST_USER_ID, attempt, 2)

    assert turns == [ConversationTurn("pregunta 2", "respuesta 2"), ConversationTurn("pregunta 3", "respuesta 3")]


def test_stores_the_citations_as_json(database, voice_schema):
    attempt = uuid4()
    VoiceExchangeRepository(database).save(exchange(attempt, "q", "a"))

    with database.transaction() as connection:
        row = connection.execute(f"SELECT citations::text, status FROM {VOICE_EXCHANGES_TABLE} "
                                 "WHERE roast_attempt_id = %s", (attempt,)).fetchone()

    assert row == ('[{"fileName": "a.pdf", "pageNumber": 2, "title": "Manual"}]', "ANSWERED")
