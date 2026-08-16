package com.aedev.myserver.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds frontend.* properties. Used to build checkout success/cancel URLs
 * without hardcoding any environment's domain into the codebase.
 */
@ConfigurationProperties(prefix = "frontend")
public record FrontendProperties(
        String url
) {
}