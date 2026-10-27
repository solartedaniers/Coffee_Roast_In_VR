package com.toastedvr.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toastedvr.ai.service.RecordedRoastSessions.RecordedSession;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class RagContextRetrievalServiceTests {

    private static Locale originalLocale;

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final RagContextRetrievalService service = new RagContextRetrievalService(vectorStore, 0.18, 0.22, 4);

    @BeforeAll
    static void useRecordedLocale() {
        originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    @AfterAll
    static void restoreLocale() {
        Locale.setDefault(originalLocale);
    }

    static Stream<RecordedSession> recordedSessions() {
        return RecordedRoastSessions.ALL.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recordedSessions")
    @SuppressWarnings("null")
    void searchesWithTheSameQueryAsTheBackendDid(RecordedSession recorded) {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        service.findRelevantChunks(recorded.request(), recorded.level());

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        SearchRequest request = captor.getValue();
        assertThat(request.getQuery() + "\ntopK=" + request.getTopK()).isEqualTo(recorded.expectedQuery());
    }

    @Test
    @SuppressWarnings("null")
    void returnsTheTextOfEachRetrievedChunk() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
            new Document("Primer fragmento.", Map.of("file_name", "manual.pdf")),
            new Document("Segundo fragmento.", Map.of())
        ));

        List<String> chunks = service.findRelevantChunks(RecordedRoastSessions.ALL.get(0).request(), null);

        assertThat(chunks).containsExactly("Primer fragmento.", "Segundo fragmento.");
    }

    @Test
    @SuppressWarnings("null")
    void returnsNoChunksWhenTheVectorStoreFails() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenThrow(new IllegalStateException("down"));

        List<String> chunks = service.findRelevantChunks(RecordedRoastSessions.ALL.get(0).request(), null);

        assertThat(chunks).isEmpty();
    }
}
