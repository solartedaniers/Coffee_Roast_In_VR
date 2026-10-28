from app.rag.document_embedder import DocumentEmbedder


class FakeEmbeddings:
    def __init__(self):
        self.batches = []

    def embed(self, texts):
        self.batches.append(texts)
        return [[float(len(text))] for text in texts]


def test_embeds_in_batches_keeping_the_order():
    embeddings = FakeEmbeddings()

    vectors = DocumentEmbedder(embeddings, batch_size=2).embed(["a", "bb", "ccc"])

    assert embeddings.batches == [["a", "bb"], ["ccc"]]
    assert vectors == [[1.0], [2.0], [3.0]]


def test_adds_the_document_prefix_only_to_what_is_embedded():
    embeddings = FakeEmbeddings()

    DocumentEmbedder(embeddings, batch_size=8, document_prefix="search_document: ").embed(["texto"])

    assert embeddings.batches == [["search_document: texto"]]
