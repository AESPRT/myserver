package com.aedev.myserver.application.dto.rag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCollectionRequest(
        @NotBlank(message = "slug is required")
        @Size(max = 100)
        String slug,

        @NotBlank(message = "name is required")
        @Size(max = 200)
        String name,

        String description
) {
}