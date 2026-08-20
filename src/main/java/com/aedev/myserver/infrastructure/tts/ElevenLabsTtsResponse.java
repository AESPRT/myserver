package com.aedev.myserver.infrastructure.tts;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ElevenLabsTtsResponse(

        @JsonProperty("audio_base64")
        String audioBase64,

        Alignment alignment,

        @JsonProperty("normalized_alignment")
        Alignment normalizedAlignment
) {

    public record Alignment(
            List<String> characters,

            @JsonProperty("character_start_times_seconds")
            List<Double> characterStartTimesSeconds,

            @JsonProperty("character_end_times_seconds")
            List<Double> characterEndTimesSeconds
    ) {
    }
}