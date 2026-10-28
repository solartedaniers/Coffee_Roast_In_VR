"""Cruza los PDFs de rag-docs/, el registro de ingesta y los fragmentos guardados."""
from dataclasses import dataclass
from enum import StrEnum

from app.rag.diagnostics.index_statistics import FileChunkStats
from app.rag.diagnostics.pdf_inspector import PdfTextReport
from app.rag.ingested_document_repository import IngestedDocument


class FileStatus(StrEnum):
    OK = "ok"
    NOT_INGESTED = "not_ingested"
    NO_CHUNKS = "no_chunks"
    COUNT_MISMATCH = "count_mismatch"
    MISSING_PDF = "missing_pdf"


@dataclass(frozen=True)
class FileDiagnosis:
    file_name: str
    status: FileStatus
    registered_chunks: int | None
    stored: FileChunkStats | None
    text: PdfTextReport | None


def classify(in_folder: bool, registered: IngestedDocument | None, stored: FileChunkStats | None) -> FileStatus:
    stored_chunks = stored.chunks if stored else 0
    if not in_folder:
        return FileStatus.MISSING_PDF
    if registered is None:
        return FileStatus.NOT_INGESTED
    if stored_chunks == 0:
        return FileStatus.NO_CHUNKS
    if registered.chunk_count != stored_chunks:
        return FileStatus.COUNT_MISMATCH
    return FileStatus.OK


def diagnose(folder_reports: list[PdfTextReport], registered: list[IngestedDocument],
             stored: list[FileChunkStats]) -> list[FileDiagnosis]:
    reports = {report.file_name: report for report in folder_reports}
    registry = {document.file_name: document for document in registered}
    stats = {item.file_name: item for item in stored}
    names = sorted(set(reports) | set(registry) | set(stats))
    return [FileDiagnosis(name, classify(name in reports, registry.get(name), stats.get(name)),
                          registry[name].chunk_count if name in registry else None,
                          stats.get(name), reports.get(name))
            for name in names]
