"""Base real para los tests de integración. Solo corren si AI_TEST_DB_URL apunta a una base
desechable (nombre terminado en _test o _e2e); usuario y contraseña salen de IA/.env."""
import os

import pytest

from app.config import Settings
from app.rag.database import Database
from app.rag.disposable_database import database_name, is_disposable
from app.rag.schema import RagSchema

TEST_DB_URL_VARIABLE = "AI_TEST_DB_URL"


@pytest.fixture
def database() -> Database:
    url = os.environ.get(TEST_DB_URL_VARIABLE)
    if not url:
        pytest.skip(f"{TEST_DB_URL_VARIABLE} is not set")
    if not is_disposable(url):
        pytest.fail(f"Refusing to use a database that is not disposable: {database_name(url)!r}")
    settings = Settings()
    return Database(url, settings.db_username, settings.db_password.get_secret_value(),
                    settings.db_connect_timeout_seconds)


@pytest.fixture
def schema(database) -> RagSchema:
    rag_schema = RagSchema(database, "rag_document_chunks_it", 3)
    rag_schema.ensure()
    yield rag_schema
    with database.transaction() as connection:
        connection.execute("DROP TABLE IF EXISTS rag_document_chunks_it")
        connection.execute("DELETE FROM rag_ingested_documents WHERE file_name LIKE 'it-%'")
