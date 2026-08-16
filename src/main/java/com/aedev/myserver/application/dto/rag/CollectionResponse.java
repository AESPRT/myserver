package com.aedev.myserver.application.dto.rag;

public record CollectionResponse(
        Long id,
        String slug,
        String name,
        String description
) {
}