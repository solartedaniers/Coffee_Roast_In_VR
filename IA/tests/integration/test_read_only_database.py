import psycopg
import pytest

from app.rag.database import Database


def test_a_read_only_connection_cannot_write(database):
    read_only = Database(database.url, database.username, database.password, database.connect_timeout_seconds,
                         read_only=True)

    with pytest.raises(psycopg.errors.ReadOnlySqlTransaction):
        with read_only.transaction() as connection:
            connection.execute("CREATE TABLE it_read_only_probe (id int)")

    with read_only.transaction() as connection:
        assert connection.execute("SELECT 1").fetchone() == (1,)
