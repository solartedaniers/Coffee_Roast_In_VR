from app.rag.database import READ_ONLY_OPTIONS, Database


def test_a_read_only_database_asks_postgres_to_refuse_writes():
    assert Database("postgresql://h/db", "u", "p", 1, read_only=True).options == READ_ONLY_OPTIONS
    assert Database("postgresql://h/db", "u", "p", 1).options is None
