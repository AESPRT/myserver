package com.aedev.myserver.application.dto.audio;

import com.aedev.myserver.domain.entity.StoryAudio;

import java.time.Instant;
import java.util.List;

public record StoryAudioResponse(
        Long id,
        String mediaId,
        String title,
        String url,
        String fileName,
        String voiceId,
        String modelId,
        Integer characterCount,
        Long fileSize,
        Instant createdAt,
        List<WordTiming> words
) {

    public static StoryAudioResponse from(StoryAudio audio) {
        return new StoryAudioResponse(
                audio.getId(),
                audio.getMediaId(),
                audio.getTitle(),
                audio.getUrl(),
                audio.getFileName(),
                audio.getVoiceId(),
                audio.getModelId(),
                audio.getCharacterCount(),
                audio.getFileSize(),
                audio.getCreatedAt(),
                audio.getWordTimings() != null
                        ? audio.getWordTimings()
                        : List.of()
        );
    }
}