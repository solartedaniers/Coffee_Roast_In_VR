import pytest

from app.feedback.text_cleaner import FeedbackTextCleaner

cleaner = FeedbackTextCleaner()


def test_keeps_only_the_text_before_the_first_blank_line():
    assert cleaner.clean("Buen tueste. Sube la carga.\n\nInstrucciones repetidas.") == "Buen tueste. Sube la carga."


def test_drops_the_trailing_partial_sentence():
    assert cleaner.clean("Buen tueste. La próxima vez sube") == "Buen tueste."


@pytest.mark.parametrize(("raw", "expected"), [
    ("  ¿Subiste el gas?  ", "¿Subiste el gas?"),
    ("¡Excelente!", "¡Excelente!"),
    ("Sin ningún final", "Sin ningún final"),
    ("", ""),
    # Java quita los espacios antes de buscar la línea en blanco.
    ("\n\nTexto tras saltos iniciales.", "Texto tras saltos iniciales."),
])
def test_cleans_like_java(raw, expected):
    assert cleaner.clean(raw) == expected
