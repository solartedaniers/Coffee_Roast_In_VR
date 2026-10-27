import logging

from app.contracts import RoastFeedbackRequest
from app.feedback.cache import FeedbackCache
from app.feedback.prompt_builder import FeedbackPromptBuilder
from app.feedback.providers.chain import ProviderChain
from app.rag.context_retriever import RagContextRetriever

log = logging.getLogger(__name__)


class FeedbackService:
    def __init__(self, retriever: RagContextRetriever, prompt_builder: FeedbackPromptBuilder,
                 chain: ProviderChain, cache: FeedbackCache, configuration: str):
        self.retriever = retriever
        self.prompt_builder = prompt_builder
        self.chain = chain
        self.cache = cache
        self.configuration = configuration

    def generate_feedback(self, session: RoastFeedbackRequest) -> str | None:
        """None si ningún proveedor respondió: la retroalimentación es un extra sobre el resultado
        ya calculado y nunca debe tumbar la consulta de la sesión."""
        key = self.cache.key_for(session, self.configuration)
        cached = self.cache.get(key)
        if cached is not None:
            return cached
        try:
            context = self.retriever.find_relevant_chunks(session, session.knowledge_level)
            generated = self.chain.generate(self.prompt_builder.build(session, session.knowledge_level, context))
        except Exception as error:
            log.warning("Could not generate feedback: %s", error)
            return None
        if generated is None:
            return None
        self.cache.put(key, generated.text)
        return generated.text
