package com.aedev.myserver.application.dto.audio;

import jakarta.validation.constraints.NotBlank;

public record GenerateStoryAudioRequest(
        @NotBlank(message = "mediaId is required") String mediaId,
        @NotBlank(message = "title is required") String title,
        @NotBlank(message = "transcript is required") String transcript
) {
}