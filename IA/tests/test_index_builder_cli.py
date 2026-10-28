import pytest

from app.rag.index_builder_cli import SERVICE_TABLE, UnsafeTarget, ensure_safe_target

COPY_URL = "postgresql://127.0.0.1:5434/toasted_vr_rag_test"


def test_accepts_a_separate_table_in_a_disposable_database():
    ensure_safe_target(COPY_URL, "rag_chunks_variant")


def test_refuses_the_real_database():
    with pytest.raises(UnsafeTarget):
        ensure_safe_target("jdbc:postgresql://localhost:5433/toasted_vr_db", "rag_chunks_variant")


def test_refuses_the_table_used_by_the_service():
    assert SERVICE_TABLE == "rag_document_chunks"
    with pytest.raises(UnsafeTarget):
        ensure_safe_target(COPY_URL, SERVICE_TABLE)
