package com.aedev.myserver.application.exception;

public class SemaphoreIntegrationException extends RuntimeException {
    public SemaphoreIntegrationException(String message) {
        super("Semaphore SMS request failed: " + message);
    }

    public SemaphoreIntegrationException(String message, Throwable cause) {
        super("Semaphore SMS request failed: " + message, cause);
    }
}