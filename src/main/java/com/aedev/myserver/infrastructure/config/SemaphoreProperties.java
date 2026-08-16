package com.aedev.myserver.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the "semaphore" block from application.yml. Kept as a dedicated
 * properties class (not @Value fields scattered across the client), so
 * every Semaphore-related setting has one source of truth and shows up
 * together in IDE autocomplete / config metadata.
 */
@ConfigurationProperties(prefix = "semaphore")
public record SemaphoreProperties(
        String apiKey,
        String apiUrl,
        String senderName
) {
}
