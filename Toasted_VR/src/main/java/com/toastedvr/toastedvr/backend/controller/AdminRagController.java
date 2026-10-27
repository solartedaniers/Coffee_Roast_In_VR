package com.toastedvr.toastedvr.backend.controller;

import com.toastedvr.toastedvr.backend.client.AiIngestionClient;
import com.toastedvr.toastedvr.backend.dto.RagIngestionSummaryResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Manual trigger only: ingestion never runs on startup, so this is called
// by hand whenever a PDF is added or changed under rag-docs/. Protected by
// the existing /api/v1/admin/** -> hasRole(ADMIN) rule in SecurityConfig.
// La ingesta la hace el servicio IA (carpeta IA/); aquí solo se delega.
@RestController
@RequestMapping("/api/v1/admin/rag")
public class AdminRagController {

    private final AiIngestionClient aiIngestionClient;

    public AdminRagController(AiIngestionClient aiIngestionClient) {
        this.aiIngestionClient = aiIngestionClient;
    }

    @PostMapping("/ingest")
    public RagIngestionSummaryResponse ingest() {
        return aiIngestionClient.ingestAll();
    }
}
