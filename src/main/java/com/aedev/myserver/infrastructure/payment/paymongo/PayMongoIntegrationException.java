package com.aedev.myserver.infrastructure.payment.paymongo;

/**
 * Thrown when PayMongo returns an unexpected or unusable response.
 * Mapped to a clean API error by GlobalExceptionHandler (Step 19).
 */
public class PayMongoIntegrationException extends RuntimeException {

    public PayMongoIntegrationException(String message) {
        super(message);
    }

    public PayMongoIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}