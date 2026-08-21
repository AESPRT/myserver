package com.aedev.myserver.application.service.audio;

import com.aedev.myserver.application.dto.audio.GenerateStoryAudioRequest;
import com.aedev.myserver.domain.entity.StoryAudio;
import com.aedev.myserver.domain.enums.StoryAudioStatus;
import com.aedev.myserver.domain.repository.StoryAudioRepository;
import com.aedev.myserver.infrastructure.audio.AudioFileStorageService;
import com.aedev.myserver.infrastructure.tts.ElevenLabsClient;
import com.aedev.myserver.infrastructure.tts.ElevenLabsProperties;
import com.aedev.myserver.infrastructure.tts.TtsSynthesisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class StoryAudioGenerationService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    StoryAudioGenerationService.class
            );

    private final StoryAudioRepository repository;
    private final ElevenLabsClient elevenLabsClient;
    private final ElevenLabsProperties properties;
    private final AudioFileStorageService storageService;

    public StoryAudioGenerationService(
            StoryAudioRepository repository,
            ElevenLabsClient elevenLabsClient,
            ElevenLabsProperties properties,
            AudioFileStorageService storageService
    ) {
        this.repository = repository;
        this.elevenLabsClient = elevenLabsClient;
        this.properties = properties;
        this.storageService = storageService;
    }

    @Async("storyAudioExecutor")
    public void generate(
            Long audioId,
            GenerateStoryAudioRequest request,
            String contentHash
    ) {
        try {
            log.info(
                    "Starting async story generation: mediaId={}, characters={}",
                    request.mediaId(),
                    request.transcript().length()
            );

            StoryAudio audio = repository.findById(audioId)
                    .orElseThrow();

            TtsSynthesisResult result =
                    elevenLabsClient.synthesize(
                            request.transcript()
                    );

            String fileName =
                    request.mediaId()
                            + "-"
                            + contentHash.substring(0, 8)
                            + ".mp3";

            AudioFileStorageService.StoredFile stored =
                    storageService.store(
                            fileName,
                            result.audioBytes()
                    );

            audio.setTitle(request.title());
            audio.setFileName(stored.fileName());
            audio.setFilePath(stored.filePath());
            audio.setUrl(stored.url());

            audio.setVoiceId(properties.voiceId());
            audio.setModelId(properties.modelId());

            audio.setCharacterCount(
                    request.transcript().length()
            );

            audio.setFileSize(
                    stored.fileSize()
            );

            audio.setContentHash(contentHash);

            audio.setWordTimings(
                    result.wordTimings()
            );

            audio.setStatus(
                    StoryAudioStatus.READY
            );

            audio.setErrorMessage(null);

            repository.save(audio);

            log.info(
                    "Story generation completed: mediaId={}, words={}",
                    request.mediaId(),
                    result.wordTimings().size()
            );

        } catch (Exception e) {

            log.error(
                    "Story audio generation failed: mediaId={}",
                    request.mediaId(),
                    e
            );

            repository.findById(audioId).ifPresent(audio -> {
                audio.setStatus(
                        StoryAudioStatus.FAILED
                );

                audio.setErrorMessage(
                        safeErrorMessage(e)
                );

                repository.save(audio);
            });
        }
    }

    private String safeErrorMessage(Exception e) {
        String message = e.getMessage();

        if (message == null || message.isBlank()) {
            return "Story audio generation failed";
        }

        return message.length() > 500
                ? message.substring(0, 500)
                : message;
    }
}