from datetime import UTC, date, datetime

from app.voice.schema import VoiceSchema
from app.voice.speech.quota import MonthlyCharacterQuota, current_month


def test_counts_by_calendar_month_in_utc():
    assert current_month(lambda: datetime(2026, 10, 31, 23, 59, tzinfo=UTC)) == date(2026, 10, 1)


def test_rejects_without_touching_the_database_what_can_never_fit():
    counter = MonthlyCharacterQuota(database=None, provider="azure", monthly_limit=100)

    assert counter.try_reserve(101) is False
    assert counter.try_reserve(0) is False


def test_only_creates_tables_if_they_do_not_exist():
    statements = [statement.as_string(None) for statement in VoiceSchema(None).statements()]

    assert statements and all(text.startswith('CREATE TABLE IF NOT EXISTS "') for text in statements)
