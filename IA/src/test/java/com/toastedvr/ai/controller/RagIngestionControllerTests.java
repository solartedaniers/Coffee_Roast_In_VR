package com.toastedvr.ai.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.toastedvr.ai.dto.RagIngestionSummaryResponse;
import com.toastedvr.ai.dto.RagIngestionSummaryResponse.FileResult;
import com.toastedvr.ai.dto.RagIngestionSummaryResponse.Status;
import com.toastedvr.ai.service.RagDocumentIngestionService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RagIngestionController.class)
class RagIngestionControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagDocumentIngestionService ragDocumentIngestionService;

    @Test
    void returnsTheIngestionSummary() throws Exception {
        when(ragDocumentIngestionService.ingestAll()).thenReturn(RagIngestionSummaryResponse.from(List.of(
            new FileResult("manual.pdf", Status.INGESTED, 40),
            new FileResult("roto.pdf", Status.FAILED, 0, "bad text")
        )));

        mockMvc.perform(post("/api/v1/rag/ingest"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.filesIngested").value(1))
            .andExpect(jsonPath("$.filesFailed").value(1))
            .andExpect(jsonPath("$.totalChunksIndexed").value(40))
            .andExpect(jsonPath("$.files[0].fileName").value("manual.pdf"))
            .andExpect(jsonPath("$.files[1].errorMessage").value("bad text"));
    }
}
