package com.aedev.myserver.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class CohereClientConfig {

    public static final String COHERE_WEB_CLIENT = "cohereWebClient";

    @Bean(COHERE_WEB_CLIENT)
    public WebClient cohereWebClient(CohereProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.apiUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
    }
}