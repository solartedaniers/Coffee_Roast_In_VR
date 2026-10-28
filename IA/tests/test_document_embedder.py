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

