package com.toastedvr.toastedvr.backend.service;

import com.toastedvr.toastedvr.backend.domain.RagIngestedDocument;
import com.toastedvr.toastedvr.backend.dto.RagIngestionSummaryResponse;
import com.toastedvr.toastedvr.backend.dto.RagIngestionSummaryResponse.FileResult;
import com.toastedvr.toastedvr.backend.dto.RagIngestionSummaryResponse.Status;
import com.toastedvr.toastedvr.backend.repository.RagIngestedDocumentRepository;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

// Reads every PDF under src/main/resources/rag-docs/, chunks it and stores
// the embeddings in pgvector. Runs only when triggered from
// AdminRagController, never on application startup, so adding PDFs never
// requires re-embedding everything: each file is skipped once its content
// hash is already recorded in RagIngestedDocument.
@Service
public class RagDocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(RagDocumentIngestionService.class);
    private static final String RAG_DOCS_LOCATION_PATTERN = "classpath*:rag-docs/*.pdf";
    private static final String FILE_NAME_METADATA_KEY = "file_name";
    private static final String CONTENT_HASH_METADATA_KEY = "content_hash";

    private final VectorStore vectorStore;
    private final RagIngestedDocumentRepository ragIngestedDocumentRepository;

    public RagDocumentIngestionService(
        VectorStore vectorStore,
        RagIngestedDocumentRepository ragIngestedDocumentRepository
    ) {
        this.vectorStore = vectorStore;
        this.ragIngestedDocumentRepository = ragIngestedDocumentRepository;
    }

    public RagIngestionSummaryResponse ingestAll() {
        List<FileResult> fileResults = new ArrayList<>();
        for (Resource pdfResource : resolvePdfResources()) {
            fileResults.add(ingestOne(pdfResource));
        }
        return RagIngestionSummaryResponse.from(fileResults);
    }

    private Resource[] resolvePdfResources() {
        try {
            return new PathMatchingResourcePatternResolver().getResources(RAG_DOCS_LOCATION_PATTERN);
        } catch (IOException e) {
            log.warn("Could not list PDFs under rag-docs/: {}", e.getMessage());
            return new Resource[0];
        }
    }

    private FileResult ingestOne(Resource pdfResource) {
        String fileName = pdfResource.getFilename();
        String contentHash;
        try {
            contentHash = sha256Of(pdfResource);
        } catch (IOException e) {
            log.warn("Could not read {}: {}", fileName, e.getMessage());
            return new FileResult(fileName, Status.READ_ERROR, 0);
        }

        Optional<RagIngestedDocument> existing = ragIngestedDocumentRepository.findByFileName(fileName);
        if (existing.isPresent() && existing.get().getContentHash().equals(contentHash)) {
            return new FileResult(fileName, Status.SKIPPED_UNCHANGED, existing.get().getChunkCount());
        }

        List<Document> chunks = readAndSplit(pdfResource, fileName, contentHash);

        if (existing.isPresent()) {
            // Content changed since the last ingest: drop the stale chunks
            // for this file before adding the new ones, otherwise both
            // versions would stay searchable side by side.
            vectorStore.delete(FILE_NAME_METADATA_KEY + " == '" + fileName + "'");
            @SuppressWarnings("null")
            RagIngestedDocument existingDocument = existing.get();
            existingDocument.markReingested(contentHash, chunks.size());
            ragIngestedDocumentRepository.save(existingDocument);
        } else {
            ragIngestedDocumentRepository.save(new RagIngestedDocument(fileName, contentHash, chunks.size()));
        }

        vectorStore.add(chunks);

        Status status = existing.isPresent() ? Status.REINGESTED : Status.INGESTED;
        return new FileResult(fileName, status, chunks.size());
    }

    private List<Document> readAndSplit(Resource pdfResource, String fileName, String contentHash) {
        List<Document> pages = new PagePdfDocumentReader(pdfResource).read();
        for (Document page : pages) {
            page.getMetadata().put(FILE_NAME_METADATA_KEY, fileName);
            page.getMetadata().put(CONTENT_HASH_METADATA_KEY, contentHash);
        }
        return new CharacterTextSplitter().apply(pages);
    }

    private static String sha256Of(Resource resource) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(inputStream.readAllBytes());
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available on this JVM", e);
        }
    }
}
