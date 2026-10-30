"""Ingesta los PDFs de rag-docs/ en la base y la tabla configuradas (IA/.env), sin pasar por el backend.

Uso: python -m app.rag.ingest_cli [--force]
Sin --force hace lo mismo que POST /api/v1/admin/rag/ingest. Con --force reingesta todos los PDFs aunque
no hayan cambiado, por ejemplo después de cambiar RAG_TEXT_SPLITTER. Cada PDF va en su propia
transacción: si uno falla, sus fragmentos anteriores siguen intactos.
"""
import argparse
import sys

from app.config import get_settings
from app.container import build_database, build_embeddings, build_http, build_ingestion
from app.contracts import IngestionStatus
from app.rag.disposable_database import database_name
from app.rag.schema import RagSchema


def main() -> None:
    parser = argparse.ArgumentParser(description="Ingest the PDFs of rag-docs/ into the configured RAG table")
    parser.add_argument("--force", action="store_true", help="Re-ingest every PDF even if it did not change")
    args = parser.parse_args()
    settings = get_settings()
    database = build_database(settings)
    RagSchema(database, settings.rag_vector_table, settings.rag_embedding_dimensions).ensure()
    print(f"Ingesting into {database_name(settings.db_url)}.{settings.rag_vector_table} "
          f"({settings.rag_text_splitter}, {settings.rag_chunk_size_chars} chars, force={args.force})")
    summary = build_ingestion(settings, database, build_embeddings(settings, build_http(settings))).ingest_all(args.force)
    for file in summary.files:
        print(f"{file.status.value:<18} {file.chunk_count:>5}  {file.file_name}  {file.error_message or ''}")
    print(f"{summary.total_chunks_indexed} chunks, {summary.files_failed} failed")
    if any(file.status is IngestionStatus.FAILED for file in summary.files):
        sys.exit(1)


if __name__ == "__main__":
    main()
