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


def test_drops_glyph_names_and_keeps_the_degree_sign():
    raw = "Received 2020 /gid00030/gid00035/gid00032 Abstract.\nScorched started at 275 /C176C (IBM/C210 SPSS)."
    [cleaned] = PdfTextCleaner(0.5).clean_pages([page(raw, 1)])

    assert cleaned.text == "Received 2020  Abstract.\nScorched started at 275 °C (IBM SPSS)."


def test_drops_manuscript_line_numbers_but_keeps_real_figures():
    raw = ("Accepted Manuscript\nWith more than 2 billion cups consumed around the globe on an everyday3\n"
           "basis, coffee is the most important beverage commodity traded in world4\n"
           "markets (Nair, 2010; Ponte, 2002). Coffee consumption rates have increased5\n"
           "1-2% per year worldwide during the last decades and the demand6\n"
           "for specialty coffee was roasted at 2107\nNormal 210 02:40 11:25 74.4")
    [cleaned] = PdfTextCleaner(0.5).clean_pages([page(raw, 1)])

    assert cleaned.text.splitlines()[1:] == [
        "With more than 2 billion cups consumed around the globe on an everyday",
        "basis, coffee is the most important beverage commodity traded in world",
        "markets (Nair, 2010; Ponte, 2002). Coffee consumption rates have increased",
        "1-2% per year worldwide during the last decades and the demand",
        "for specialty coffee was roasted at 210",
        "Normal 210 02:40 11:25 74.4",
    ]


def test_keeps_a_short_run_of_consecutive_numbers_at_line_ends():
    raw = "Tueste a 200\nSecado de 5 a 6\nCrack a los 8\nFinal en 9\nDesarrollo 10\nPeso 11"
    [cleaned] = PdfTextCleaner(0.5).clean_pages([page(raw, 1)])

    assert cleaned.text == raw


def test_keeps_the_axis_values_of_a_chart():
    raw = "Figura 2. Azufre\n0,0\n0,1\n0,2\n0,3\n0,4\n0,5\n0,6"
    [cleaned] = PdfTextCleaner(0.5).clean_pages([page(raw, 1)])

    assert cleaned.text == raw


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
