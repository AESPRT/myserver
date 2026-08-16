package com.aedev.myserver.infrastructure.audio;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.audio")
public record AudioProperties(
        String storagePath,
        String publicBaseUrl
) {
}