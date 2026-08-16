package com.aedev.myserver.infrastructure.ai.cohere.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Cohere Chat API (POST /v2/chat) with RAG via the "documents" field.
 * Cohere's chat models are trained specifically to ground answers in
 * documents passed this way and produce inline citations -- this is
 * why we don't hand-roll a "stuff retrieved chunks into the prompt"
 * approach; Cohere's native grounding is both more reliable and gives
 * us citation data back that manual prompt-stuffing wouldn't.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CohereChatRequest(
        @JsonProperty("model") String model,
        @JsonProperty("messages") List<CohereChatMessage> messages,
        @JsonProperty("documents") List<CohereRagDocument> documents,
        @JsonProperty("temperature") Double temperature
) {
    public record CohereChatMessage(
            @JsonProperty("role") String role,
            @JsonProperty("content") String content
    ) {
        public static CohereChatMessage user(String content) {
            return new CohereChatMessage("user", content);
        }
    }

    /**
     * Cohere's documents field accepts a flat object per document; "id"
     * lets citations in the response reference back to which chunk they
     * came from.
     */
    public record CohereRagDocument(
            @JsonProperty("id") String id,
            @JsonProperty("data") DocumentData data
    ) {
        public record DocumentData(
                @JsonProperty("text") String text
        ) {
        }

        public static CohereRagDocument of(String id, String text) {
            return new CohereRagDocument(id, new DocumentData(text));
        }
    }
}