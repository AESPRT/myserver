package com.aedev.myserver.application.exception;

public class SamePlanChangeException extends RuntimeException {
    public SamePlanChangeException() {
        super("You are already subscribed to this exact plan and billing cycle");
    }
}