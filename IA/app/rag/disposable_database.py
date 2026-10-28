from urllib.parse import urlsplit

from app.rag.database import to_libpq_url

DISPOSABLE_SUFFIXES = ("_test", "_e2e")


def database_name(db_url: str) -> str:
    return urlsplit(to_libpq_url(db_url)).path.lstrip("/")


def is_disposable(db_url: str) -> bool:
    """Solo las bases cuyo nombre termina en _test o _e2e se pueden modificar en pruebas."""
    return database_name(db_url).endswith(DISPOSABLE_SUFFIXES)
