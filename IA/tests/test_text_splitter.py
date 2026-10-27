import pytest

from app.rag.document import Document
from app.rag.text_splitter import CharacterTextSplitter, java_strip

splitter = CharacterTextSplitter(700, 100)


def sequential_words(count: int) -> str:
    return "".join(f"word{index} " for index in range(count))


def test_keeps_short_text_as_one_chunk():
    short_text = "A short paragraph well under the chunk size."

    chunks = splitter.split_documents([Document(short_text)])

    assert [chunk.text for chunk in chunks] == [short_text]


def test_splits_long_text_into_overlapping_chunks():
    chunks = splitter.split_documents([Document(sequential_words(400))])

    assert len(chunks) > 1
    for previous, current in zip(chunks, chunks[1:]):
        assert current.text[:20] in previous.text


def test_propagates_source_metadata_to_every_chunk():
    chunks = splitter.split_documents([Document(sequential_words(400), {"file_name": "example.pdf"})])

    assert chunks
    assert all(chunk.metadata == {"file_name": "example.pdf"} for chunk in chunks)


def test_does_not_share_the_metadata_dictionary_between_chunks():
    chunks = splitter.split_documents([Document(sequential_words(400), {"file_name": "example.pdf"})])

    chunks[0].metadata["file_name"] = "changed.pdf"

    assert chunks[1].metadata["file_name"] == "example.pdf"


def test_skips_chunks_that_are_only_whitespace():
    assert splitter.split_text(" " * 1500) == []


def test_strips_like_java():
    assert java_strip("\t texto \n") == "texto"
    assert java_strip(" texto ") == " texto "


def test_rejects_an_overlap_as_large_as_the_chunk():
    with pytest.raises(ValueError):
        CharacterTextSplitter(100, 100)
