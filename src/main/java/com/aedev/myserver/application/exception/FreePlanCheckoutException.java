package com.aedev.myserver.application.exception;

public class FreePlanCheckoutException extends RuntimeException {
    public FreePlanCheckoutException() {
        super("Cannot create a paid checkout session for the free plan");
    }
}