"""Vectores de conformidad: las muestras de cada curva de prueba y las alertas esperadas por nivel.

Unity corre su evaluador en C# sobre las mismas muestras y debe entregar exactamente lo mismo.
Uso: python -m app.alerts.conformance  (regenera IA/eval/alerts/conformance.json)
"""
import argparse
import json
import re
from pathlib import Path

from app.alerts.catalog import AlertCatalog
from app.alerts.evaluator import AlertDelivery, AlertEvaluator
from app.alerts.synthetic_curve import CurveSet, SyntheticCurve
from app.config import SERVICE_ROOT, get_settings
from app.domain import KnowledgeLevel

DEFAULT_CURVES = SERVICE_ROOT / "eval" / "alerts" / "curves.json"
DEFAULT_OUTPUT = SERVICE_ROOT / "eval" / "alerts" / "conformance.json"
SAMPLE_FIELDS = ["phase", "temperature", "roastSeconds", "firstCrackReached", "moisturePct", "recording",
                 "assistantBusy"]
SCALAR_LIST = re.compile(r"\[\s+([^\[\]{}]*?)\s+\]")
WHITESPACE = re.compile(r"\s+")


def run_curve(catalog: AlertCatalog, curve: SyntheticCurve, level: KnowledgeLevel) -> list[AlertDelivery]:
    evaluator = AlertEvaluator(catalog, level)
    deliveries = (evaluator.step(sample, curve.is_recording(index), curve.is_assistant_busy(index))
                  for index, sample in enumerate(curve.roast_samples()))
    return [delivery for delivery in deliveries if delivery is not None]


def delivery_to_dict(delivery: AlertDelivery) -> dict:
    return {"second": delivery.second, "roastSeconds": delivery.roast_seconds, "alertId": delivery.alert_id,
            "mode": delivery.mode.value}


def build_vectors(catalog: AlertCatalog, curves: CurveSet) -> dict:
    vectors = []
    for curve in curves.curves:
        samples = [[s.phase.value, s.temperature, s.roast_seconds, s.first_crack_reached, s.moisture_pct,
                    curve.is_recording(index), curve.is_assistant_busy(index)]
                   for index, s in enumerate(curve.roast_samples())]
        expected = {level.value: [delivery_to_dict(d) for d in run_curve(catalog, curve, level)]
                    for level in KnowledgeLevel}
        vectors.append({"curve": curve.id, "description": curve.description, "samples": samples,
                        "expected": expected})
    return {"schemaVersion": catalog.schema_version, "catalogRevision": catalog.catalog_revision,
            "sampleFields": SAMPLE_FIELDS, "vectors": vectors}


def render(vectors: dict) -> str:
    # Cada lista de valores simples (una muestra) en una sola línea, para que el diff se pueda leer.
    text = json.dumps(vectors, ensure_ascii=False, indent=1)
    return SCALAR_LIST.sub(lambda match: "[" + WHITESPACE.sub(" ", match.group(1)) + "]", text) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description="Regenerate the proactive alert conformance vectors")
    parser.add_argument("--curves", type=Path, default=DEFAULT_CURVES)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    catalog = AlertCatalog.from_file(get_settings().alerts_catalog_file)
    args.output.write_text(render(build_vectors(catalog, CurveSet.from_file(args.curves))), encoding="utf-8")
    print(f"Wrote {args.output}")


if __name__ == "__main__":
    main()
