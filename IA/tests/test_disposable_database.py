import pytest

from app.rag.disposable_database import is_disposable


@pytest.mark.parametrize(("url", "expected"), [
    ("postgresql://127.0.0.1:5434/toasted_vr_rag_test", True),
    ("jdbc:postgresql://localhost:5433/toasted_vr_e2e", True),
    ("jdbc:postgresql://localhost:5433/toasted_vr_db", False),
    ("postgresql://localhost/test_toasted", False),
])
def test_only_databases_ending_in_test_or_e2e_are_disposable(url, expected):
    assert is_disposable(url) is expected
