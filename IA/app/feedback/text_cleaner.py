from app.java_format import java_strip

BLANK_LINE = "\n\n"
SENTENCE_ENDINGS = ".!?"


class FeedbackTextCleaner:
    """Deja solo la respuesta útil del modelo, igual que el servicio en Java."""

    def clean(self, raw: str) -> str:
        text = java_strip(raw)
        # Tras una línea en blanco el modelo suele repetir instrucciones o inventar otra sección.
        blank_line = text.find(BLANK_LINE)
        if blank_line >= 0:
            text = java_strip(text[:blank_line])
        return self._drop_trailing_partial_sentence(text)

    @staticmethod
    def _drop_trailing_partial_sentence(text: str) -> str:
        # El límite de tokens corta a mitad de palabra; se recorta hasta la última oración completa.
        if not text or text[-1] in SENTENCE_ENDINGS:
            return text
        last_end = max(text.rfind(ending) for ending in SENTENCE_ENDINGS)
        return java_strip(text[:last_end + 1]) if last_end >= 0 else text
