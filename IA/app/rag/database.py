from collections.abc import Iterator
from contextlib import contextmanager

import psycopg

JDBC_PREFIX = "jdbc:"


def to_libpq_url(db_url: str) -> str:
    """Acepta el DB_URL en formato JDBC que usaba el servicio en Java."""
    return db_url[len(JDBC_PREFIX):] if db_url.startswith(JDBC_PREFIX) else db_url


class Database:
    def __init__(self, db_url: str, username: str, password: str, connect_timeout_seconds: int):
        self.url = to_libpq_url(db_url)
        self.username = username
        self.password = password
        self.connect_timeout_seconds = connect_timeout_seconds

    @contextmanager
    def transaction(self) -> Iterator[psycopg.Connection]:
        """Confirma al salir sin errores y deshace todo si algo falla."""
        with psycopg.connect(self.url, user=self.username, password=self.password,
                             connect_timeout=self.connect_timeout_seconds) as connection:
            yield connection
