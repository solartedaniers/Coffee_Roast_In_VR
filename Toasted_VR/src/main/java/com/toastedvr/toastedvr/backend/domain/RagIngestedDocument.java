package com.toastedvr.toastedvr.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

// Tracks which PDFs under rag-docs/ have already been embedded into the
// vector store, keyed by file name plus a content hash so re-running the
// ingestion endpoint is idempotent: unchanged files are skipped, changed
// files trigger a re-embed.
@Entity
@Table(name = "rag_ingested_documents")
public class RagIngestedDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String fileName;

    @Column(nullable = false, length = 64)
    private String contentHash;

    @Column(nullable = false)
    private Integer chunkCount;

    @Column(nullable = false)
    private Instant ingestedAt;

    protected RagIngestedDocument() {
    }

    public RagIngestedDocument(String fileName, String contentHash, Integer chunkCount) {
        this.fileName = fileName;
        this.contentHash = contentHash;
        this.chunkCount = chunkCount;
        this.ingestedAt = Instant.now();
    }

    public void markReingested(String contentHash, Integer chunkCount) {
        this.contentHash = contentHash;
        this.chunkCount = chunkCount;
        this.ingestedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentHash() {
        return contentHash;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }
}
