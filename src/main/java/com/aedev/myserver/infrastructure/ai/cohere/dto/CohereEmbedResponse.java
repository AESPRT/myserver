package com.aedev.myserver.infrastructure.ai.cohere.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CohereEmbedResponse(
        @JsonProperty("id") String id,
        @JsonProperty("embeddings") Embeddings embeddings
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Embeddings(
            @JsonProperty("float") List<List<Double>> floatEmbeddings
    ) {
    }
}