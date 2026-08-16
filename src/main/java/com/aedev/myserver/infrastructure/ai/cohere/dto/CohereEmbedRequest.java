package com.aedev.myserver.infrastructure.ai.cohere.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Cohere Embed API (POST /v2/embed). input_type differs between
 * indexing and querying -- Cohere's models are trained to produce
 * asymmetric embeddings depending on which side of the search a text is
 * on, so using the wrong input_type measurably hurts retrieval quality.
 * "search_document" for text being stored, "search_query" for the
 * user's question at retrieval time.
 */
public record CohereEmbedRequest(
        @JsonProperty("model") String model,
        @JsonProperty("texts") List<String> texts,
        @JsonProperty("input_type") String inputType,
        @JsonProperty("embedding_types") List<String> embeddingTypes
) {
    public static CohereEmbedRequest forDocuments(String model, List<String> texts) {
        return new CohereEmbedRequest(model, texts, "search_document", List.of("float"));
    }

    public static CohereEmbedRequest forQuery(String model, String query) {
        return new CohereEmbedRequest(model, List.of(query), "search_query", List.of("float"));
    }
}