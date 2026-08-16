package com.aedev.myserver.application.dto.rag;

public record IngestDocumentResponse(
        Long documentId,
        int chunkCount
) {
}