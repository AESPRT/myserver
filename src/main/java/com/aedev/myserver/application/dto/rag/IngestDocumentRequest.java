package com.aedev.myserver.application.dto.rag;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record IngestDocumentRequest(
        @NotBlank(message = "content is required")
        String content,

        String title,

        /* Caller-supplied id for upsert -- re-ingesting the same externalId replaces prior chunks. */
        String externalId,

        Map<String, Object> metadata
) {
}