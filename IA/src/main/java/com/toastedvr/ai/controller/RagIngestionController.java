package com.toastedvr.ai.controller;

import com.toastedvr.ai.dto.RagIngestionSummaryResponse;
import com.toastedvr.ai.service.RagDocumentIngestionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Lo llama el backend desde POST /api/v1/admin/rag/ingest, que es donde se
// valida que quien lo pide sea ADMIN.
@RestController
@RequestMapping("/api/v1/rag")
public class RagIngestionController {

    private final RagDocumentIngestionService ragDocumentIngestionService;

    public RagIngestionController(RagDocumentIngestionService ragDocumentIngestionService) {
        this.ragDocumentIngestionService = ragDocumentIngestionService;
    }

    @PostMapping("/ingest")
    public RagIngestionSummaryResponse ingest() {
        return ragDocumentIngestionService.ingestAll();
    }
}
