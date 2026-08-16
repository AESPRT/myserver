package com.aedev.myserver.application.exception;

public class InvalidPlanException extends RuntimeException {
    public InvalidPlanException(String packageId) {
        super("Invalid or unknown plan: " + packageId);
    }
}