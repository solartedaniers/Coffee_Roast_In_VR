"""Endpoints que usa el backend. El contrato es el mismo del servicio en Java."""
import logging
from contextlib import asynccontextmanager

from fastapi import APIRouter, FastAPI, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.container import Services
from app.contracts import FeedbackResponse, RagIngestionSummaryResponse, RoastFeedbackRequest

log = logging.getLogger(__name__)


def build_router(services: Services) -> APIRouter:
    router = APIRouter(prefix="/api/v1")

    @router.post("/feedback", response_model=FeedbackResponse)
    def generate_feedback(request: RoastFeedbackRequest) -> FeedbackResponse:
        return FeedbackResponse(feedback=services.feedback.generate_feedback(request))

    # Lo llama el backend desde POST /api/v1/admin/rag/ingest, donde se valida que sea ADMIN.
    @router.post("/rag/ingest", response_model=RagIngestionSummaryResponse)
    def ingest() -> RagIngestionSummaryResponse:
        summary = services.ingestion.ingest_all()
        if summary.files_ingested or summary.files_reingested:
            # Con material nuevo, la retroalimentación guardada ya no refleja el contexto actual.
            services.feedback_cache.clear()
        return summary

    return router


def create_app(services: Services, ensure_schema: bool = True) -> FastAPI:
    @asynccontextmanager
    async def lifespan(app: FastAPI):
        if ensure_schema:
            try:
                services.schema.ensure()
            except Exception as error:
                # Igual que en Java el RAG es opcional: sin base, la retroalimentación sigue sin contexto.
                log.warning("Could not prepare the RAG tables: %s", error)
        yield

    app = FastAPI(title="Toasted VR AI", lifespan=lifespan)

    @app.exception_handler(RequestValidationError)
    async def bad_request(request: Request, error: RequestValidationError) -> JSONResponse:
        # Spring respondía 400 (no 422) ante un JSON mal formado o con valores inválidos.
        return JSONResponse(status_code=status.HTTP_400_BAD_REQUEST, content={"detail": "Invalid request body"})

    app.include_router(build_router(services))
    return app
