package com.toastedvr.toastedvr.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class CharacterTextSplitterTests {

    private final CharacterTextSplitter splitter = new CharacterTextSplitter();

    @Test
    void keepsShortTextAsOneChunk() {
        @SuppressWarnings("null")
        Document shortDoc = new Document("A short paragraph well under the chunk size.", Map.of());

        List<Document> chunks = splitter.apply(List.of(shortDoc));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getText()).isEqualTo(shortDoc.getText());
    }

    @Test
    void splitsLongTextIntoOverlappingChunks() {
        // Positional, non-repeating content (unlike "xxxx...") so the
        // overlap check below actually proves chunk[i+1] starts with text
        // taken from chunk[i], not just a coincidence of repeated characters.
        @SuppressWarnings("null")
        Document longDoc = new Document(sequentialWords(400), Map.of());

        List<Document> chunks = splitter.apply(List.of(longDoc));

        assertThat(chunks.size()).isGreaterThan(1);
        // Each chunk is independently .strip()-ped, which can shift the exact
        // boundary by a few characters whenever it lands on whitespace, so
        // this checks that the start of a chunk appears somewhere in the
        // previous chunk (real overlap), not that it lines up at the exact
        // same offset.
        for (int i = 1; i < chunks.size(); i++) {
            String previous = chunks.get(i - 1).getText();
            String current = Objects.requireNonNull(chunks.get(i).getText());
            String anchor = current.substring(0, Math.min(20, current.length()));
            assertThat(previous).contains(anchor);
        }
    }

    @Test
    void propagatesSourceMetadataToEveryChunk() {
        @SuppressWarnings("null")
        Document longDoc = new Document(sequentialWords(400), Map.of("file_name", "example.pdf"));

        List<Document> chunks = splitter.apply(List.of(longDoc));

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getMetadata()).containsEntry("file_name", "example.pdf"));
    }

    private static String sequentialWords(int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            text.append("word").append(i).append(' ');
        }
        return text.toString();
    }
}
