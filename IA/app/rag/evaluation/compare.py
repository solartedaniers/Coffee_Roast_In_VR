"""Tabla comparativa en Markdown de las mediciones guardadas en IA/eval/results/.

Uso: python -m app.rag.evaluation.compare [archivo.json ...]
Sin argumentos toma todas las mediciones, en orden de nombre. La primera es la línea base.
"""
import argparse
import json
from pathlib import Path

from app.rag.evaluation.__main__ import DEFAULT_RESULTS_DIR

COLUMNS = ("hit@1", "hit@3", "hit@4", "hit@5", "MRR@10", "Δ MRR", "neg. con contexto", "repetidos", "p50 ms")
GROUP_COLUMNS = ("preguntas", "hit@1", "hit@3", "hit@4", "hit@5", "MRR@10")
GROUP_LABELS = {
    "confirmed_with_draft_pages": "validadas + páginas en borrador",
    "draft_questions": "preguntas en borrador",
    "total": "total",
}


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


def group_rows(report: dict) -> list[list[str]]:
    summary = report["summary"]
    groups = ({"validadas": summary["player"]} | summary.get("by_group", {})
              | {GROUP_LABELS[name]: values for name, values in summary["groups"].items() if values["cases"]})
    return [[report["label"], name, str(values["cases"]), *(f"{values[f'hit_at_{k}']:.2f}" for k in (1, 3, 4, 5)),
             f"{values['mrr_at_10']:.3f}"] for name, values in groups.items()]


def group_table(reports: list[dict]) -> str:
    """Separa las preguntas validadas de las que están en borrador (solo mediciones que las tienen)."""
    rows = [cells for report in reports if "groups" in report["summary"] for cells in group_rows(report)]
    if not rows:
        return ""
    lines = ["| Variante | Grupo | " + " | ".join(GROUP_COLUMNS) + " |", "|---" * (len(GROUP_COLUMNS) + 2) + "|"]
    lines += ["| " + " | ".join(cells) + " |" for cells in rows]
    return "\n".join(lines)


def load_reports(paths: list[Path]) -> list[dict]:
    # En eval/results/ también están el diagnóstico y la validación de la rúbrica: solo cuentan las mediciones del RAG.
    reports = [json.loads(path.read_text(encoding="utf-8")) for path in paths]
    return [report for report in reports if "player" in report.get("summary", {})]


def main() -> None:
    parser = argparse.ArgumentParser(description="Compare saved RAG measurements")
    parser.add_argument("paths", nargs="*", type=Path)
    args = parser.parse_args()
    paths = args.paths or sorted(DEFAULT_RESULTS_DIR.glob("*.json"))
    reports = load_reports(paths)
    print(markdown_table(reports))
    groups = group_table(reports)
    if groups:
        print("\n" + groups)


if __name__ == "__main__":
    main()
