package com.aedev.myserver.application.exception;

public class WebhookSignatureException extends RuntimeException {
    public WebhookSignatureException() {
        super("Invalid PayMongo webhook signature. The request could not be verified as originating from PayMongo.");
    }
}