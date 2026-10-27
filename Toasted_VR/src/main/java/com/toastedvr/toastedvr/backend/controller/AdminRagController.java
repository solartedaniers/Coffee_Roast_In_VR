package com.toastedvr.toastedvr.backend.controller;

import com.toastedvr.toastedvr.backend.dto.RagIngestionSummaryResponse;
import com.toastedvr.toastedvr.backend.service.RagDocumentIngestionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Manual trigger only: ingestion never runs on startup, so this is called
// by hand whenever a PDF is added or changed under rag-docs/. Protected by
// the existing /api/v1/admin/** -> hasRole(ADMIN) rule in SecurityConfig.
@RestController
@RequestMapping("/api/v1/admin/rag")
public class AdminRagController {

    private final RagDocumentIngestionService ragDocumentIngestionService;

    public AdminRagController(RagDocumentIngestionService ragDocumentIngestionService) {
        this.ragDocumentIngestionService = ragDocumentIngestionService;
    }

    @PostMapping("/ingest")
    public RagIngestionSummaryResponse ingest() {
        return ragDocumentIngestionService.ingestAll();
    }
}
