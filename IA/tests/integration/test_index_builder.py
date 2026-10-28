from app.rag.document_embedder import DocumentEmbedder
from app.rag.index_builder import RagIndexBuilder
from app.rag.pdf_reader import PdfPageReader
from app.rag.text_splitter import CharacterTextSplitter
from tests.support.pdf_factory import write_text_pdf

TABLE = "rag_chunks_builder_it"


class FakeEmbeddings:
    def embed(self, texts):
        return [[1.0, 0.0, 0.0] for _ in texts]


def test_replaces_the_whole_table_with_the_new_chunks(database, tmp_path):
    builder = RagIndexBuilder(database, PdfPageReader(), CharacterTextSplitter(700, 100),
                              DocumentEmbedder(FakeEmbeddings(), 8), dimensions=3)
    first = write_text_pdf(tmp_path / "it-a.pdf", ["uno", "dos"])
    second = write_text_pdf(tmp_path / "it-b.pdf", ["tres"])
    try:
        builder.build(TABLE, [first, second])
        built = builder.build(TABLE, [second])

        with database.transaction() as connection:
            rows = connection.execute(
                f"SELECT content, metadata->>'file_name', (metadata->>'page_number')::int FROM {TABLE}").fetchall()
    finally:
        with database.transaction() as connection:
            connection.execute(f"DROP TABLE IF EXISTS {TABLE}")

    assert built.chunks_per_file == {"it-b.pdf": 1}
    assert rows == [("tres", "it-b.pdf", 1)]
