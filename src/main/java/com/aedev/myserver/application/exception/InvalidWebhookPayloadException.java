package com.aedev.myserver.application.exception;

public class InvalidWebhookPayloadException extends RuntimeException {
    public InvalidWebhookPayloadException(String reason) {
        super("Received an invalid PayMongo webhook payload: " + reason);
    }
}