"""Tablas propias del asistente de voz. Se crean solo si no existen, igual que las del RAG."""
from psycopg import sql

from app.rag.database import Database
from app.voice.history import history_statements
from app.voice.speech.quota import TTS_USAGE_TABLE


class VoiceSchema:
    def __init__(self, database: Database):
        self.database = database

    def statements(self) -> list[sql.Composable]:
        return [
            sql.SQL("CREATE TABLE IF NOT EXISTS {} (month date NOT NULL, provider varchar(20) NOT NULL, "
                    "characters bigint NOT NULL, updated_at timestamp(6) with time zone NOT NULL, "
                    "PRIMARY KEY (month, provider))").format(sql.Identifier(TTS_USAGE_TABLE)),
            *history_statements(),
        ]

    def ensure(self) -> None:
        with self.database.transaction() as connection:
            for statement in self.statements():
                connection.execute(statement)
