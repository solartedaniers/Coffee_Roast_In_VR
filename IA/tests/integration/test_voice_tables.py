from datetime import UTC, datetime

import pytest

from app.voice.schema import VoiceSchema
from app.voice.speech.quota import TTS_USAGE_TABLE, MonthlyCharacterQuota

TEST_PROVIDER = "it-azure"


@pytest.fixture
def voice_schema(database):
    VoiceSchema(database).ensure()
    yield
    with database.transaction() as connection:
        connection.execute(f"DELETE FROM {TTS_USAGE_TABLE} WHERE provider = %s", (TEST_PROVIDER,))


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
