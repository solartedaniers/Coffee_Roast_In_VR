"""Reindexa rag-docs/ en una tabla aparte de una base desechable, para medir variantes del RAG.

Uso: python -m app.rag.index_builder_cli --table rag_chunks_bge_m3
Toma el modelo, las dimensiones y la partición de la configuración (IA/.env y
variables de entorno). Se niega a correr sobre una base que no termine en _test o _e2e y sobre la
tabla que usa el servicio, para no tocar nunca el índice real.
"""
import argparse
import sys

from app.config import Settings, get_settings
from app.container import build_database, build_embeddings, build_http, build_text_splitter
from app.rag.disposable_database import database_name, is_disposable
from app.rag.document_embedder import DocumentEmbedder
from app.rag.index_builder import RagIndexBuilder
from app.rag.pdf_catalog import PdfCatalog
from app.rag.pdf_reader import PdfPageReader

SERVICE_TABLE = Settings.model_fields["rag_vector_table"].default


class UnsafeTarget(Exception):
    """La reindexación podría tocar la base o la tabla reales."""


def ensure_safe_target(db_url: str, table: str) -> None:
    if not is_disposable(db_url):
        raise UnsafeTarget(f"Refusing to write to a database that is not disposable: {database_name(db_url)!r}")
    if table == SERVICE_TABLE:
        raise UnsafeTarget(f"Refusing to overwrite the service table {SERVICE_TABLE!r}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Rebuild the RAG index into a separate table of a disposable database")
    parser.add_argument("--table", required=True)
    args = parser.parse_args()
    settings = get_settings()
    try:
        ensure_safe_target(settings.db_url, args.table)
    except UnsafeTarget as error:
        sys.exit(str(error))

    embedder = DocumentEmbedder(build_embeddings(settings, build_http(settings)), settings.rag_embedding_batch_size)
    builder = RagIndexBuilder(build_database(settings), PdfPageReader(), build_text_splitter(settings), embedder,
                              settings.rag_embedding_dimensions)
    catalog = PdfCatalog(settings.rag_docs_dir)
    built = builder.build(args.table, [settings.rag_docs_dir / name for name in catalog.file_names()])
    for file_name, chunks in built.chunks_per_file.items():
        print(f"{chunks:>5}  {file_name}")
    print(f"{sum(built.chunks_per_file.values())} chunks in {built.table}")


if __name__ == "__main__":
    main()
