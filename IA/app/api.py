"""Endpoints que usa el backend. El contrato es el mismo del servicio en Java."""
import logging
import threading
from contextlib import asynccontextmanager

from fastapi import APIRouter, FastAPI, File, Form, Request, UploadFile, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import ValidationError

from app.container import Services
from app.contracts import FeedbackResponse, RagIngestionSummaryResponse, RoastFeedbackRequest
from app.voice.contracts import VoiceAnswerResponse, VoiceErrorResponse, VoiceQuestionContext
from app.voice.errors import VoiceErrorCode, VoiceRequestError
from app.voice.response_mapper import to_response

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

    if services.voice is not None:
        voice, max_audio_bytes = services.voice, services.voice_max_audio_bytes

        # Lo llama el backend desde POST /api/v1/roasting/assistant/questions con el usuario ya autenticado.
        @router.post("/voice/questions", response_model=VoiceAnswerResponse)
        def ask_by_voice(audio: UploadFile = File(...), context: str = Form(...)) -> VoiceAnswerResponse:
            try:
                question_context = VoiceQuestionContext.model_validate_json(context)
            except ValidationError:
                raise VoiceRequestError(VoiceErrorCode.CONTEXT_INVALID) from None
            # Se lee un byte de más para saber si se pasó del límite sin cargar archivos enormes.
            wav = audio.file.read(max_audio_bytes + 1)
            return to_response(voice.ask(question_context, wav))

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
            if services.voice_schema is not None:
                try:
                    services.voice_schema.ensure()
                except Exception as error:
                    log.warning("Could not prepare the voice assistant tables: %s", error)
        if services.voice is not None:
            # En segundo plano para no demorar el arranque si Azure tarda o no hay Internet.
            threading.Thread(target=services.voice.warm_up, args=(services.voice_warm_up_seconds,),
                             daemon=True).start()
        yield

    app = FastAPI(title="Toasted VR AI", lifespan=lifespan)

    @app.exception_handler(RequestValidationError)
    async def bad_request(request: Request, error: RequestValidationError) -> JSONResponse:
        # Spring respondía 400 (no 422) ante un JSON mal formado o con valores inválidos.
        return JSONResponse(status_code=status.HTTP_400_BAD_REQUEST, content={"detail": "Invalid request body"})

    @app.exception_handler(VoiceRequestError)
    async def voice_request_error(request: Request, error: VoiceRequestError) -> JSONResponse:
        body = VoiceErrorResponse(code=error.code.value, message=services.voice_messages.error(error.code))
        return JSONResponse(status_code=error.http_status, content=body.model_dump(by_alias=True))

    app.include_router(build_router(services))
    return app
