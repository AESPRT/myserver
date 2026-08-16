package com.aedev.myserver.application.exception;

public class NoExistingSubscriptionException extends RuntimeException {
    public NoExistingSubscriptionException() {
        super("No existing subscription found for this user. Use checkout to create a new subscription instead.");
    }
}