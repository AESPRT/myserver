package com.aedev.myserver.application.dto.rag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RagQueryRequest(
        @NotBlank(message = "question is required")
        String question,

        @Min(1) @Max(20)
        Integer topK
) {
    public int resolvedTopK() {
        return topK != null ? topK : 5;
    }
}