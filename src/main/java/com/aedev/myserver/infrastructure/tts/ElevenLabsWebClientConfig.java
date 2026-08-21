package com.aedev.myserver.infrastructure.tts;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class ElevenLabsWebClientConfig {

    @Bean("elevenLabsWebClient")
    public WebClient elevenLabsWebClient(ElevenLabsProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.apiUrl())
                .defaultHeader("xi-api-key", properties.apiKey())
                .defaultHeader("Content-Type", "application/json")
                .codecs(configurer ->
                        configurer
                                .defaultCodecs()
                                .maxInMemorySize(
                                        100 * 1024 * 1024
                                )
                )
                .build();
    }
}