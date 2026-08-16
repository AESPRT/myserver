package com.aedev.myserver.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppApiKeyProperties(
        String apiKey
) {
}