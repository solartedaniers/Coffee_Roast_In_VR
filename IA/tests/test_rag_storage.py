from app.rag.database import Database, to_libpq_url
from app.rag.schema import RagSchema
from app.rag.vector_store import vector_literal


def test_accepts_the_jdbc_url_used_by_java():
    assert to_libpq_url("jdbc:postgresql://localhost:5433/toasted_vr_db") == "postgresql://localhost:5433/toasted_vr_db"
    assert to_libpq_url("postgresql://localhost:5433/toasted_vr_db") == "postgresql://localhost:5433/toasted_vr_db"


def test_writes_vectors_in_pgvector_format():
    assert vector_literal([0.5, -1, 2.25]) == "[0.5,-1.0,2.25]"


def test_creates_the_same_tables_as_spring_only_if_missing():
    schema = RagSchema(Database("jdbc:postgresql://localhost/db", "user", "", 5), "rag_document_chunks", 768)

    statements = [statement.as_string(None) for statement in schema.statements()]

    assert all("IF NOT EXISTS" in statement for statement in statements)
    assert any('"rag_document_chunks" (id uuid' in statement and "vector(768)" in statement for statement in statements)
    assert any('"rag_document_chunks_index"' in statement and "vector_cosine_ops" in statement
               for statement in statements)
    assert any('"rag_ingested_documents"' in statement and "file_name varchar(255) NOT NULL UNIQUE" in statement
               for statement in statements)
