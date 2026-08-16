package com.aedev.myserver.infrastructure.payment.paymongo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
public class PayMongoWebClientConfig {

    /**
     * Dedicated WebClient for PayMongo, pre-configured with base URL and
     * Basic Auth (secretKey:). PayMongo's API uses HTTP Basic auth with the
     * secret key as the username and an empty password -- this matches the
     * original Node.js implementation's "PAYMONGO_SECRET_KEY:" pattern.
     */
    @Bean
    public WebClient payMongoWebClient(PayMongoProperties properties) {
        String credentials = properties.secretKey() + ":";
        String encoded = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        return WebClient.builder()
                .baseUrl(properties.apiUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoded)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
    }
}