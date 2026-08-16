package com.aedev.myserver.infrastructure.tts;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "elevenlabs")
public record ElevenLabsProperties(
        String apiKey,
        String voiceId,
        String modelId,
        String apiUrl
) {
}