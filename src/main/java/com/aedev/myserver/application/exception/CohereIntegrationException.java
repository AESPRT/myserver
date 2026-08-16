package com.aedev.myserver.application.exception;

public class CohereIntegrationException extends RuntimeException {

    public CohereIntegrationException(String message) {
        super(message);
    }

    public CohereIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}