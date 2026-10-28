from collections.abc import Iterator
from contextlib import contextmanager

import psycopg

JDBC_PREFIX = "jdbc:"
READ_ONLY_OPTIONS = "-c default_transaction_read_only=on"


def to_libpq_url(db_url: str) -> str:
    """Acepta el DB_URL en formato JDBC que usaba el servicio en Java."""
    return db_url[len(JDBC_PREFIX):] if db_url.startswith(JDBC_PREFIX) else db_url


class Database:
    def __init__(self, db_url: str, username: str, password: str, connect_timeout_seconds: int,
                 read_only: bool = False):
        self.url = to_libpq_url(db_url)
        self.username = username
        self.password = password
        self.connect_timeout_seconds = connect_timeout_seconds
        # Las herramientas de medición y diagnóstico lo usan para no poder escribir por error.
        self.options = READ_ONLY_OPTIONS if read_only else None

    @contextmanager
    def transaction(self) -> Iterator[psycopg.Connection]:
        """Confirma al salir sin errores y deshace todo si algo falla."""
        with psycopg.connect(self.url, user=self.username, password=self.password,
                             connect_timeout=self.connect_timeout_seconds, options=self.options) as connection:
            yield connection
