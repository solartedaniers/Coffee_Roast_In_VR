"""Mide la recuperación del RAG con la configuración actual (IA/.env y variables de entorno).

Uso: python -m app.rag.evaluation --label linea-base
Solo lee la base (conexión de solo lectura) y escribe el resultado en IA/eval/results/<label>.json.
"""
import argparse
import json
from datetime import UTC, datetime
from pathlib import Path

from app.config import SERVICE_ROOT, Settings, get_settings
from app.container import build_chunk_search, build_database, build_embeddings, build_http
from app.rag.evaluation.eval_set import EvalSetLoader
from app.rag.evaluation.evaluator import RetrievalEvaluator, result_to_dict, summarize
from app.rag.pdf_catalog import PdfCatalog

DEFAULT_CASES = SERVICE_ROOT / "eval" / "rag_questions.json"
DEFAULT_RESULTS_DIR = SERVICE_ROOT / "eval" / "results"
DEFAULT_DEPTH = 10
RAG_SETTING_PREFIX = "rag_"
# La ruta absoluta depende de cada equipo; no aporta a la medición.
EXCLUDED_SETTINGS = {"rag_docs_dir", "rag_document_titles_file"}


def rag_configuration(settings: Settings) -> dict:
    """Solo los parámetros del RAG (ninguno es secreto), para saber qué se midió."""
    values = settings.model_dump(mode="json")
    return {name: value for name, value in values.items()
            if name.startswith(RAG_SETTING_PREFIX) and name not in EXCLUDED_SETTINGS}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Evaluate RAG retrieval against the evaluation set")
    parser.add_argument("--label", required=True, help="Name of this measurement (used as file name)")
    parser.add_argument("--cases", type=Path, default=DEFAULT_CASES)
    parser.add_argument("--results-dir", type=Path, default=DEFAULT_RESULTS_DIR)
    parser.add_argument("--depth", type=int, default=DEFAULT_DEPTH)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    settings = get_settings()
    # Al medir se exigen todos los PDFs: si falta uno local, sus preguntas saldrían como fallos falsos.
    cases = EvalSetLoader(PdfCatalog(settings.rag_docs_dir)).load(args.cases)
    search = build_chunk_search(settings, build_database(settings, read_only=True),
                                build_embeddings(settings, build_http(settings)))
    results = RetrievalEvaluator(search, args.depth, settings.rag_top_k).run(cases)
    report = {
        "label": args.label,
        "measured_at": datetime.now(UTC).isoformat(timespec="seconds"),
        "configuration": rag_configuration(settings),
        "summary": summarize(results, settings.rag_top_k),
        "cases": [result_to_dict(result) for result in results],
    }
    args.results_dir.mkdir(parents=True, exist_ok=True)
    output = args.results_dir / f"{args.label}.json"
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report["summary"], ensure_ascii=False, indent=2))
    print(f"Saved {output}")


if __name__ == "__main__":
    main()
