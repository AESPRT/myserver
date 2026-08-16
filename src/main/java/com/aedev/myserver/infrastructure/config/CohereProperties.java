package com.aedev.myserver.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cohere")
public record CohereProperties(
        String apiUrl,
        String apiKey,
        String embedModel,
        String chatModel
) {
}