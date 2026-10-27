import pytest

from app.config import Settings


@pytest.fixture
def settings(monkeypatch) -> Settings:
    """Valores por defecto, sin leer IA/.env ni variables del sistema."""
    for name in Settings.model_fields:
        monkeypatch.delenv(name.upper(), raising=False)
    return Settings(_env_file=None)
