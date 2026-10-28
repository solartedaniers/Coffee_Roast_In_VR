"""Contador propio de caracteres por mes de Azure, para no pasarse de la capa gratuita (F0).

Azure no ofrece una consulta del consumo en tiempo real, así que llevo la cuenta en la base. Se
reserva antes de sintetizar y se devuelve si Azure falla: así dos preguntas a la vez no se pasan.
"""
from datetime import UTC, date, datetime
from typing import Protocol

from psycopg import sql

from app.rag.database import Database

TTS_USAGE_TABLE = "tts_monthly_usage"


class CharacterQuota(Protocol):
    def try_reserve(self, characters: int) -> bool: ...

    def release(self, characters: int) -> None: ...


def current_month(clock=lambda: datetime.now(UTC)) -> date:
    return clock().date().replace(day=1)


class MonthlyCharacterQuota:
    def __init__(self, database: Database, provider: str, monthly_limit: int, clock=lambda: datetime.now(UTC)):
        self.database = database
        self.provider = provider
        self.monthly_limit = monthly_limit
        self.clock = clock

    def try_reserve(self, characters: int) -> bool:
        """True si los caracteres caben en el mes y quedaron sumados; nunca deja pasar el límite."""
        if characters <= 0 or characters > self.monthly_limit:
            return False
        statement = sql.SQL(
            "INSERT INTO {table} (month, provider, characters, updated_at) VALUES (%s, %s, %s, now()) "
            "ON CONFLICT (month, provider) DO UPDATE SET characters = {table}.characters + EXCLUDED.characters, "
            "updated_at = now() WHERE {table}.characters + EXCLUDED.characters <= %s RETURNING characters"
        ).format(table=sql.Identifier(TTS_USAGE_TABLE))
        with self.database.transaction() as connection:
            row = connection.execute(statement, (current_month(self.clock), self.provider, characters,
                                                 self.monthly_limit)).fetchone()
        return row is not None

    def release(self, characters: int) -> None:
        statement = sql.SQL(
            "UPDATE {table} SET characters = GREATEST(characters - %s, 0), updated_at = now() "
            "WHERE month = %s AND provider = %s"
        ).format(table=sql.Identifier(TTS_USAGE_TABLE))
        with self.database.transaction() as connection:
            connection.execute(statement, (characters, current_month(self.clock), self.provider))

    def used(self) -> int:
        statement = sql.SQL("SELECT characters FROM {table} WHERE month = %s AND provider = %s").format(
            table=sql.Identifier(TTS_USAGE_TABLE))
        with self.database.transaction() as connection:
            row = connection.execute(statement, (current_month(self.clock), self.provider)).fetchone()
        return row[0] if row else 0
