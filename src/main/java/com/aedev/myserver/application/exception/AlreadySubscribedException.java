package com.aedev.myserver.application.exception;

public class AlreadySubscribedException extends RuntimeException {
    public AlreadySubscribedException() {
        super("You already have an active subscription to this plan and billing cycle");
    }
}