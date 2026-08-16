package com.aedev.myserver.infrastructure.payment.paymongo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the paymongo.* properties from application.properties.
 * secretKey must never be logged or included in any response body.
 */
@ConfigurationProperties(prefix = "paymongo")
public record PayMongoProperties(
        String secretKey,
        String apiUrl
) {
}