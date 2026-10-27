from app.rag.document import Document
from app.rag.ingested_document_repository import IngestedDocumentRepository
from app.rag.vector_store import PgVectorStore


def test_creating_the_schema_twice_changes_nothing(schema):
    schema.ensure()


def test_finds_the_closest_chunks_first_and_skips_opposite_ones(schema, database):
    with database.transaction() as connection:
        store = PgVectorStore(connection, schema.vector_table)
        store.add(
            [Document("cerca", {"file_name": "it-a.pdf"}), Document("lejos", {"file_name": "it-a.pdf"}),
             Document("opuesto", {"file_name": "it-b.pdf"})],
            [[1.0, 0.0, 0.0], [0.6, 0.8, 0.0], [-1.0, 0.0, 0.0]],
        )

        found = store.similarity_search([1.0, 0.0, 0.0], 4)

    assert [chunk.text for chunk in found] == ["cerca", "lejos"]
    assert found[0].score == 1.0
    assert found[0].metadata == {"file_name": "it-a.pdf"}


def test_deletes_only_the_chunks_of_one_file(schema, database):
    with database.transaction() as connection:
        store = PgVectorStore(connection, schema.vector_table)
        store.add([Document("a", {"file_name": "it-a.pdf"}), Document("b", {"file_name": "it-b.pdf"})],
                  [[1.0, 0.0, 0.0], [1.0, 0.0, 0.0]])

        store.delete_by_file_name("it-a.pdf")

        assert [chunk.text for chunk in store.similarity_search([1.0, 0.0, 0.0], 4)] == ["b"]


def test_records_and_updates_ingested_documents(schema, database):
    with database.transaction() as connection:
        repository = IngestedDocumentRepository(connection)
        repository.save_new("it-doc.pdf", "a" * 64, 3)
        saved = repository.find_by_file_name("it-doc.pdf")
        repository.mark_reingested(saved.id, "b" * 64, 5)
        updated = repository.find_by_file_name("it-doc.pdf")
        missing = repository.find_by_file_name("it-missing.pdf")

    assert (saved.content_hash, saved.chunk_count) == ("a" * 64, 3)
    assert (updated.content_hash, updated.chunk_count) == ("b" * 64, 5)
    assert missing is None


def test_finds_chunks_by_their_words_in_spanish_and_keeps_their_similarity(schema, database):
    with database.transaction() as connection:
        store = PgVectorStore(connection, schema.vector_table)
        store.add(
            [Document("El grano horneado sabe a pan", {"file_name": "it-a.pdf"}),
             Document("Los granos horneados pierden acidez y el horneado domina", {"file_name": "it-a.pdf"}),
             Document("Curva de enfriamiento", {"file_name": "it-b.pdf"})],
            [[1.0, 0.0, 0.0], [0.6, 0.8, 0.0], [0.0, 1.0, 0.0]],
        )

        found = store.lexical_search("¿Qué es un café horneado?", [1.0, 0.0, 0.0], "spanish", 10)
        nothing = store.lexical_search("¿?", [1.0, 0.0, 0.0], "spanish", 10)

    assert [chunk.text for chunk in found] == ["Los granos horneados pierden acidez y el horneado domina",
                                               "El grano horneado sabe a pan"]
    assert [round(chunk.score, 2) for chunk in found] == [0.6, 1.0]
    assert all(chunk.chunk_id for chunk in found)
    assert nothing == []
