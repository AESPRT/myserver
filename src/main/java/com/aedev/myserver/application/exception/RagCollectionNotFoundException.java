package com.aedev.myserver.application.exception;

public class RagCollectionNotFoundException extends RuntimeException {

    public RagCollectionNotFoundException(String slug) {
        super("RAG collection not found: " + slug);
    }
}