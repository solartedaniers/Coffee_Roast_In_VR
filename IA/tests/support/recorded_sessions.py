"""Sesiones cuyas salidas (prompt y búsqueda del RAG) se grabaron con el código en Java.

Se comparan después de normalizar CRLF, igual que hacía el test en Java.
"""
from dataclasses import dataclass
from pathlib import Path

from app.contracts import RoastFeedbackRequest
from app.domain import KnowledgeLevel, RoastingResult

SNAPSHOTS_DIR = Path(__file__).resolve().parents[2] / "src" / "test" / "resources" / "snapshots"


@dataclass(frozen=True)
class RecordedSession:
    name: str
    request: RoastFeedbackRequest
    level: KnowledgeLevel | None
    context: list[str] | None

    def expected_prompt(self) -> str:
        return read_snapshot(f"{self.name}-prompt.txt")

    def expected_query(self) -> str:
        return read_snapshot(f"{self.name}-query.txt")


def read_snapshot(file_name: str) -> str:
    return (SNAPSHOTS_DIR / file_name).read_bytes().decode("utf-8").replace("\r\n", "\n")


def _request(charge, target, duration, final, result, score, first_crack, development) -> RoastFeedbackRequest:
    return RoastFeedbackRequest(
        charge_temperature=charge, target_temperature=target, total_duration_seconds=duration,
        final_temperature=final, result=result, quality_score=score, first_crack_reached=first_crack,
        development_time_seconds=development,
    )


ALL = [
    RecordedSession("perfect", _request(190.0, 200.0, 780, 200.0, RoastingResult.PERFECT, 92, True, 156),
                    KnowledgeLevel.BEGINNER, ["Fragmento uno.", "Fragmento dos."]),
    RecordedSession("burned", _request(230.0, 205.0, 600, 228.0, RoastingResult.BURNED, 30, True, 60),
                    KnowledgeLevel.ADVANCED, []),
    RecordedSession("raw", _request(None, 210.0, 500, 180.0, RoastingResult.RAW, 20, False, None), None, None),
    RecordedSession("baked", _request(150.0, 215.0, 900, 150.0, RoastingResult.BAKED, 15, True, 300),
                    KnowledgeLevel.INTERMEDIATE, ["Contexto."]),
]
