from app.container import build_text_splitter
from app.rag.document import Document
from app.rag.pdf_text_cleaner import PdfTextCleaner
from app.rag.sentence_splitter import CleaningTextSplitter, SentenceTextSplitter
from app.rag.text_splitter import CharacterTextSplitter

HEADER = "Cenicafé, 73(1) 2022"


def page(text, number):
    return Document(text, {"file_name": "a.pdf", "page_number": number})


def test_joins_words_cut_at_the_end_of_a_line_and_keeps_compound_words():
    [cleaned] = PdfTextCleaner(0.5).clean_pages([page("El tos -\ntado y el energy-\nproducing ﬁnal", 1)])

    assert cleaned.text == "El tostado y el energy-producing final"


def test_removes_headers_repeated_across_pages_and_page_numbers():
    bodies = ["El grano se seca.", "Empieza el crack.", "Se enfría rápido."]
    pages = [page(f"{HEADER}\n{20 + n}\n{body}", n) for n, body in enumerate(bodies, start=1)]

    cleaned = PdfTextCleaner(0.5).clean_pages(pages)

    assert [item.text for item in cleaned] == bodies
    assert [item.metadata["page_number"] for item in cleaned] == [1, 2, 3]


def test_drops_the_bibliography_only_in_the_second_half():
    pages = [page("Índice\nReferencias", 1), page("Texto", 2), page("Fin del texto.\nReferencias\nAutor, 2020", 3),
             page("Autor, 2021", 4)]

    cleaned = PdfTextCleaner(1.0).clean_pages(pages)

    assert [item.text for item in cleaned] == ["Índice\nReferencias", "Texto", "Fin del texto.", ""]


def test_packs_whole_sentences_without_cutting_them():
    splitter = SentenceTextSplitter(chunk_size_chars=40, chunk_overlap_chars=0, min_chunk_chars=0)

    chunks = splitter.split_text("Primera frase corta. Segunda frase también. Tercera frase final.")

    assert chunks == ["Primera frase corta.", "Segunda frase también.", "Tercera frase final."]


def test_overlaps_the_last_sentences_that_fit():
    splitter = SentenceTextSplitter(chunk_size_chars=30, chunk_overlap_chars=12, min_chunk_chars=0)

    chunks = splitter.split_text("Uno dos tres. Cuatro. Cinco seis siete ocho.")

    assert chunks == ["Uno dos tres. Cuatro.", "Cuatro. Cinco seis siete ocho."]


def test_cuts_a_sentence_longer_than_the_chunk_and_drops_tiny_chunks():
    splitter = SentenceTextSplitter(chunk_size_chars=10, chunk_overlap_chars=0, min_chunk_chars=4)

    chunks = splitter.split_text("abcdefghijklmnopqrstu. Ok.")

    # La frase larga se corta en trozos de 10; "." y "Ok." quedan por debajo del mínimo.
    assert chunks == ["abcdefghij", "klmnopqrst", "u. Ok."]


def test_keeps_the_page_of_each_chunk():
    splitter = SentenceTextSplitter(chunk_size_chars=100, chunk_overlap_chars=0, min_chunk_chars=0)

    chunks = splitter.split_documents([page("Frase de la página uno.", 1), page("Frase de la página dos.", 2)])

    assert [(chunk.text, chunk.metadata["page_number"], chunk.metadata["chunk_index"]) for chunk in chunks] == [
        ("Frase de la página uno.", 1, 0), ("Frase de la página dos.", 2, 0)]


def test_the_container_builds_the_configured_splitter(settings):
    assert isinstance(build_text_splitter(settings), CharacterTextSplitter)

    sentences = build_text_splitter(settings.model_copy(update={"rag_text_splitter": "sentences"}))

    assert isinstance(sentences, CleaningTextSplitter)
    assert isinstance(sentences.splitter, SentenceTextSplitter)
