"""Diagnóstico del índice del RAG: fragmentos por archivo, archivos fallidos y PDFs sin texto.

Uso: python -m app.rag.diagnostics [--output IA/eval/results/diagnostico-indice.json]
Solo lee: la conexión a la base es de solo lectura.
"""
import argparse
import json
import logging
from dataclasses import asdict
from pathlib import Path

from app.config import SERVICE_ROOT, get_settings
from app.container import build_database
from app.rag.diagnostics.index_diagnosis import diagnose
from app.rag.diagnostics.index_statistics import IndexStatistics
from app.rag.diagnostics.pdf_inspector import PYPDF_LOGGER, PdfTextInspector
from app.rag.ingested_document_repository import IngestedDocumentRepository
from app.rag.pdf_catalog import PdfCatalog

DEFAULT_OUTPUT = SERVICE_ROOT / "eval" / "results" / "diagnostico-indice.json"
DEFAULT_SHORT_CHUNK_CHARS = 120
DEFAULT_MIN_PAGE_CHARS = 50


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Read-only diagnosis of the RAG index")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--short-chunk-chars", type=int, default=DEFAULT_SHORT_CHUNK_CHARS)
    parser.add_argument("--min-page-chars", type=int, default=DEFAULT_MIN_PAGE_CHARS)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    # Los avisos de pypdf se cuentan en el reporte; en consola solo serían ruido.
    logging.getLogger(PYPDF_LOGGER).propagate = False
    settings = get_settings()
    catalog = PdfCatalog(settings.rag_docs_dir)
    inspector = PdfTextInspector(args.min_page_chars)
    reports = [inspector.inspect(settings.rag_docs_dir / name) for name in catalog.file_names()]
    with build_database(settings, read_only=True).transaction() as connection:
        statistics = IndexStatistics(connection, settings.rag_vector_table)
        stored = statistics.per_file(args.short_chunk_chars)
        duplicated = statistics.duplicated_chunks()
        registered = IngestedDocumentRepository(connection).find_all()

    files = diagnose(reports, registered, stored)
    report = {
        "thresholds": {"short_chunk_chars": args.short_chunk_chars, "min_page_chars": args.min_page_chars},
        "totals": {"files": len(files), "chunks": sum(item.chunks for item in stored),
                   "duplicated_chunks": duplicated,
                   "short_chunks": sum(item.short_chunks for item in stored),
                   "files_not_ok": [item.file_name for item in files if item.status != "ok"]},
        "files": [asdict(item) for item in files],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for item in files:
        text = item.text
        print(f"{item.status:<15} {item.file_name[:50]:<50} chunks={item.stored.chunks if item.stored else 0:<4} "
              f"pages={text.pages if text else '-'} lang={text.language if text else '-'} "
              f"empty_pages={text.pages_without_text if text else '-'} "
              f"references_from={text.references_start_page if text else '-'}")
    print(json.dumps(report["totals"], ensure_ascii=False))
    print(f"Saved {args.output}")


if __name__ == "__main__":
    main()
