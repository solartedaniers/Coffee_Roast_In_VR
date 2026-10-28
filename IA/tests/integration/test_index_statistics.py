from app.rag.diagnostics.index_statistics import FileChunkStats, IndexStatistics
from app.rag.document import Document
from app.rag.ingested_document_repository import IngestedDocumentRepository
from app.rag.vector_store import PgVectorStore


def test_counts_chunks_per_file_and_duplicates(schema, database):
    with database.transaction() as connection:
        PgVectorStore(connection, schema.vector_table).add(
            [Document("texto largo", {"file_name": "it-a.pdf", "page_number": 1}),
             Document("corto", {"file_name": "it-a.pdf", "page_number": 2}),
             Document("corto", {"file_name": "it-b.pdf"})],
            [[1.0, 0.0, 0.0]] * 3,
        )
        statistics = IndexStatistics(connection, schema.vector_table)

        per_file = statistics.per_file(short_chunk_chars=6)
        duplicated = statistics.duplicated_chunks()

    assert per_file == [FileChunkStats("it-a.pdf", 2, 8, 1, 0, 2), FileChunkStats("it-b.pdf", 1, 5, 1, 1, 0)]
    assert duplicated == 1


def test_lists_every_ingested_document(schema, database):
    with database.transaction() as connection:
        repository = IngestedDocumentRepository(connection)
        repository.save_new("it-z.pdf", "a" * 64, 2)
        repository.save_new("it-y.pdf", "b" * 64, 1)

        names = [document.file_name for document in repository.find_all() if document.file_name.startswith("it-")]

    assert names == ["it-y.pdf", "it-z.pdf"]
