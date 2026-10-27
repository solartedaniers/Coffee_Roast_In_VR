package com.toastedvr.toastedvr.backend.dto;

import java.util.List;

public record RagIngestionSummaryResponse(
    int filesIngested,
    int filesReingested,
    int filesSkipped,
    int filesFailed,
    int totalChunksIndexed,
    List<FileResult> files
) {

    @SuppressWarnings("null")
    public static RagIngestionSummaryResponse from(List<FileResult> files) {
        int ingested = countByStatus(files, Status.INGESTED);
        int reingested = countByStatus(files, Status.REINGESTED);
        int skipped = countByStatus(files, Status.SKIPPED_UNCHANGED);
        int failed = countByStatus(files, Status.READ_ERROR) + countByStatus(files, Status.FAILED);
        int totalChunks = files.stream().mapToInt(FileResult::chunkCount).sum();

        return new RagIngestionSummaryResponse(ingested, reingested, skipped, failed, totalChunks, files);
    }

    private static int countByStatus(List<FileResult> files, Status status) {
        return (int) files.stream().filter(file -> file.status() == status).count();
    }

    public record FileResult(String fileName, Status status, int chunkCount, String errorMessage) {

        public FileResult(String fileName, Status status, int chunkCount) {
            this(fileName, status, chunkCount, null);
        }
    }

    public enum Status {
        INGESTED,
        REINGESTED,
        SKIPPED_UNCHANGED,
        READ_ERROR,
        // Fallo durante split/embedding/insert (después de leer el PDF), a
        // diferencia de READ_ERROR que es un fallo leyendo el archivo mismo.
        FAILED
    }
}
