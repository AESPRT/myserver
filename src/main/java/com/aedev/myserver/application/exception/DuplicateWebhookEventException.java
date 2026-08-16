package com.aedev.myserver.application.exception;

public class DuplicateWebhookEventException extends RuntimeException {
    public DuplicateWebhookEventException(String eventId) {
        super("This PayMongo webhook event (id: " + eventId + ") has already been processed. Skipping to avoid duplicate side effects.");
    }
}