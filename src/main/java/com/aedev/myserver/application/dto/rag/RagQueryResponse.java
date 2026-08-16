package com.aedev.myserver.application.dto.rag;

import java.util.List;

public record RagQueryResponse(
        String answer,
        List<SourceChunk> sources
) {
    public record SourceChunk(
            Long chunkId,
            Long documentId,
            String documentTitle,
            String content
    ) {
    }
}