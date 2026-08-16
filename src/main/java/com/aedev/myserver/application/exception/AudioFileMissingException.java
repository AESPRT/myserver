package com.aedev.myserver.application.exception;

public class AudioFileMissingException extends RuntimeException {
    public AudioFileMissingException(String mediaId) {
        super("Database record exists for mediaId '" + mediaId + "' but the audio file is missing on disk");
    }
}