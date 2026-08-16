package com.aedev.myserver.infrastructure.tts;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
public class ElevenLabsIntegrationException extends RuntimeException {

    private final HttpStatusCode upstreamStatus;

    public ElevenLabsIntegrationException(String message, HttpStatusCode upstreamStatus) {
        super(message);
        this.upstreamStatus = upstreamStatus;
    }

    public ElevenLabsIntegrationException(String message, HttpStatusCode upstreamStatus, Throwable cause) {
        super(message, cause);
        this.upstreamStatus = upstreamStatus;
    }

}