"""Corre la rúbrica con Groq real sobre los casos de validación y guarda el resultado.

    .venv/Scripts/python scripts/evaluation_validation.py
    .venv/Scripts/python scripts/evaluation_validation.py --runs 5 --pause 15 --only good raw

Usa la configuración de EVALUATION_GROQ_* del .env. Por defecto escribe
eval/results/evaluation-<versión de la rúbrica>.json.
"""
import argparse
import json
import sys
from pathlib import Path

from app.config import SERVICE_ROOT, get_settings
from app.container import build_evaluation, build_http
from app.evaluation.validation import ValidationRunner, load_cases

DEFAULT_CASES = SERVICE_ROOT / "eval" / "evaluation" / "cases.json"
RESULTS_DIR = SERVICE_ROOT / "eval" / "results"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--cases", type=Path, default=DEFAULT_CASES)
    parser.add_argument("--runs", type=int, default=5)
    parser.add_argument("--pause", type=float, default=10, help="segundos entre llamadas (límite de tokens de Groq)")
    parser.add_argument("--retries", type=int, default=3)
    parser.add_argument("--only", nargs="*", help="ids de los casos a correr")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    settings = get_settings()
    service = build_evaluation(settings, build_http(settings))
    cases = [case for case in load_cases(args.cases) if not args.only or case.trace.id in args.only]
    output = args.output or RESULTS_DIR / f"evaluation-{service.rubric.rubric_version}.json"
    header = {"rubricVersion": service.rubric.rubric_version, "model": settings.evaluation_groq_model,
              "temperature": settings.evaluation_groq_temperature, "seed": settings.evaluation_groq_seed,
              "reasoningEffort": settings.evaluation_groq_reasoning_effort}

    def save(partial: dict) -> None:
        output.write_text(json.dumps(header | partial, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")

    runner = ValidationRunner(service, args.runs, args.pause, args.retries, progress=lambda line: print(line, flush=True))
    report = header | runner.run(cases, on_case=save)
    summary = report["summary"]
    print(json.dumps({key: value for key, value in summary.items() if key != "byCriterion"}, ensure_ascii=False,
                     indent=1))
    print(json.dumps(summary["byCriterion"], ensure_ascii=False, indent=1))
    if report["stoppedReason"]:
        print(f"Detenida por {report['stoppedReason']}: {len(report['cases'])} de {report['plannedCases']} casos")
    print(f"Resultado en {output}")
    complete = not report["stoppedReason"] and not summary["incompleteCases"]
    return 0 if complete and not summary["justificationsWithInternalNames"] else 1


if __name__ == "__main__":
    sys.exit(main())
