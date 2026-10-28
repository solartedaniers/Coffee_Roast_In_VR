"""Tabla comparativa en Markdown de las mediciones guardadas en IA/eval/results/.

Uso: python -m app.rag.evaluation.compare [archivo.json ...]
Sin argumentos toma todas las mediciones, en orden de nombre. La primera es la línea base.
"""
import argparse
import json
from pathlib import Path

from app.rag.evaluation.__main__ import DEFAULT_RESULTS_DIR

DIAGNOSIS_FILE = "diagnostico-indice.json"
COLUMNS = ("hit@1", "hit@3", "hit@4", "hit@5", "MRR@10", "Δ MRR", "neg. con contexto", "repetidos", "p50 ms")


def row(report: dict, baseline_mrr: float) -> list[str]:
    player = report["summary"]["player"]
    negative = report["summary"]["negative"]
    return [
        report["label"],
        *(f"{player[f'hit_at_{k}']:.2f}" for k in (1, 3, 4, 5)),
        f"{player['mrr_at_10']:.3f}",
        f"{player['mrr_at_10'] - baseline_mrr:+.3f}",
        f"{negative['cases_with_context']}/{negative['cases']}",
        str(report["summary"]["repeated_texts_in_top_k"]),
        f"{report['summary']['latency_ms']['p50']:.0f}",
    ]


def markdown_table(reports: list[dict]) -> str:
    if not reports:
        return ""
    baseline_mrr = reports[0]["summary"]["player"]["mrr_at_10"]
    lines = ["| Variante | " + " | ".join(COLUMNS) + " |", "|---" * (len(COLUMNS) + 1) + "|"]
    lines += ["| " + " | ".join(row(report, baseline_mrr)) + " |" for report in reports]
    return "\n".join(lines)


def load_reports(paths: list[Path]) -> list[dict]:
    return [json.loads(path.read_text(encoding="utf-8")) for path in paths]


def main() -> None:
    parser = argparse.ArgumentParser(description="Compare saved RAG measurements")
    parser.add_argument("paths", nargs="*", type=Path)
    args = parser.parse_args()
    paths = args.paths or sorted(path for path in DEFAULT_RESULTS_DIR.glob("*.json") if path.name != DIAGNOSIS_FILE)
    print(markdown_table(load_reports(paths)))


if __name__ == "__main__":
    main()
