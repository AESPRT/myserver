package com.aedev.myserver.infrastructure.ai.cohere.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CohereChatResponse(
        @JsonProperty("id") String id,
        @JsonProperty("message") ResponseMessage message,
        @JsonProperty("citations") List<Citation> citations
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResponseMessage(
            @JsonProperty("role") String role,
            @JsonProperty("content") List<ContentBlock> content
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentBlock(
            @JsonProperty("type") String type,
            @JsonProperty("text") String text
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Citation(
            @JsonProperty("start") Integer start,
            @JsonProperty("end") Integer end,
            @JsonProperty("text") String text,
            @JsonProperty("sources") List<Source> sources
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Source(
            @JsonProperty("id") String id,
            @JsonProperty("type") String type
    ) {
    }
}