package com.aedev.myserver.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class SemaphoreClientConfig {

    public static final String SEMAPHORE_WEB_CLIENT = "semaphoreWebClient";

    @Bean(SEMAPHORE_WEB_CLIENT)
    public WebClient semaphoreWebClient(SemaphoreProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.apiUrl())
                .defaultHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();
    }
}
