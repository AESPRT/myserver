package com.aedev.myserver.application.exception;

public class DuplicateCheckoutException extends RuntimeException {
    public DuplicateCheckoutException(String referenceNumber) {
        super("A checkout for this plan is already in progress (reference: " + referenceNumber + "). Please complete or wait for it to expire before retrying.");
    }
}