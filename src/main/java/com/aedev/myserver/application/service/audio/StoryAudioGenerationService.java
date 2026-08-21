package com.aedev.myserver.application.service.audio;

import com.aedev.myserver.application.dto.audio.GenerateStoryAudioRequest;
import com.aedev.myserver.application.dto.audio.WordTiming;
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

import java.util.List;

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

    /**
     * Full TTS generation.
     * <p>
     * Use only when:
     * - no MP3 exists
     * - transcript changed
     * - existing MP3 file is actually missing
     */
    @Async("storyAudioExecutor")
    public void generate(
            Long audioId,
            GenerateStoryAudioRequest request,
            String expectedContentHash
    ) {
        try {

            log.info(
                    "Starting full story TTS generation: mediaId={}, characters={}",
                    request.mediaId(),
                    request.transcript().length()
            );

            /*
             * Generate audio + timestamps.
             *
             * This is the expensive TTS operation.
             */
            TtsSynthesisResult result =
                    elevenLabsClient.synthesize(
                            request.transcript()
                    );

            /*
             * Reload AFTER ElevenLabs finishes.
             *
             * The transcript might have changed while
             * ElevenLabs was generating.
             */
            StoryAudio audio =
                    repository.findById(audioId)
                            .orElseThrow(
                                    () -> new IllegalStateException(
                                            "Story audio record disappeared: "
                                                    + audioId
                                    )
                            );

            /*
             * Prevent an older async worker from
             * overwriting newer content.
             */
            if (
                    !expectedContentHash.equals(
                            audio.getContentHash()
                    )
            ) {
                log.warn(
                        "Discarding stale TTS generation: mediaId={}, expectedHash={}, currentHash={}",
                        request.mediaId(),
                        expectedContentHash,
                        audio.getContentHash()
                );

                return;
            }

            String fileName =
                    request.mediaId()
                            + "-"
                            + expectedContentHash.substring(
                            0,
                            8
                    )
                            + ".mp3";

            AudioFileStorageService.StoredFile stored =
                    storageService.store(
                            fileName,
                            result.audioBytes()
                    );

            audio.setTitle(
                    request.title()
            );

            audio.setFileName(
                    stored.fileName()
            );

            audio.setFilePath(
                    stored.filePath()
            );

            audio.setUrl(
                    stored.url()
            );

            audio.setVoiceId(
                    properties.voiceId()
            );

            audio.setModelId(
                    properties.modelId()
            );

            audio.setCharacterCount(
                    request.transcript().length()
            );

            audio.setFileSize(
                    stored.fileSize()
            );

            audio.setContentHash(
                    expectedContentHash
            );

            audio.setWordTimings(
                    result.wordTimings()
            );

            audio.setStatus(
                    StoryAudioStatus.READY
            );

            audio.setErrorMessage(null);

            repository.save(audio);

            log.info(
                    "Full story generation completed: mediaId={}, words={}, file={}",
                    request.mediaId(),
                    result.wordTimings().size(),
                    stored.fileName()
            );

        } catch (Exception e) {

            log.error(
                    "Full story generation failed: mediaId={}",
                    request.mediaId(),
                    e
            );

            markFailedIfCurrent(
                    audioId,
                    expectedContentHash,
                    e
            );
        }
    }

    /**
     * Generate ONLY word timing information using the
     * already-existing MP3.
     * <p>
     * No new speech is generated.
     */
    @Async("storyAudioExecutor")
    public void generateWordTimingsOnly(
            Long audioId,
            String transcript,
            String expectedContentHash
    ) {
        try {

            StoryAudio audio =
                    repository.findById(audioId)
                            .orElseThrow(
                                    () -> new IllegalStateException(
                                            "Story audio not found: "
                                                    + audioId
                                    )
                            );

            /*
             * Verify that this alignment request still
             * belongs to the current transcript.
             */
            if (
                    !expectedContentHash.equals(
                            audio.getContentHash()
                    )
            ) {
                log.warn(
                        "Skipping stale forced alignment before request: audioId={}",
                        audioId
                );

                return;
            }

            String filePath =
                    audio.getFilePath();

            if (
                    filePath == null ||
                            filePath.isBlank()
            ) {
                throw new IllegalStateException(
                        "Story audio has no file path"
                );
            }

            if (
                    !storageService.exists(
                            filePath
                    )
            ) {
                throw new IllegalStateException(
                        "Existing story audio file is missing"
                );
            }

            log.info(
                    "Starting forced alignment only: mediaId={}, audioId={}, file={}",
                    audio.getMediaId(),
                    audioId,
                    audio.getFileName()
            );

            /*
             * IMPORTANT:
             *
             * Existing MP3 is sent to ElevenLabs.
             *
             * No TTS generation occurs here.
             */
            List<WordTiming> timings =
                    elevenLabsClient.alignExistingAudio(
                            filePath,
                            transcript
                    );

            /*
             * Reload once alignment is complete.
             */
            audio =
                    repository.findById(audioId)
                            .orElseThrow(
                                    () -> new IllegalStateException(
                                            "Story audio record disappeared: "
                                                    + audioId
                                    )
                            );

            /*
             * Transcript might have changed during
             * forced alignment.
             */
            if (
                    !expectedContentHash.equals(
                            audio.getContentHash()
                    )
            ) {
                log.warn(
                        "Discarding stale forced alignment result: audioId={}, expectedHash={}, currentHash={}",
                        audioId,
                        expectedContentHash,
                        audio.getContentHash()
                );

                return;
            }

            audio.setWordTimings(
                    timings
            );

            /*
             * Keep all existing audio information:
             *
             * URL
             * fileName
             * filePath
             * fileSize
             *
             * Only word timing metadata changes.
             */
            audio.setStatus(
                    StoryAudioStatus.READY
            );

            audio.setErrorMessage(null);

            repository.save(audio);

            log.info(
                    "Forced alignment completed: mediaId={}, audioId={}, words={}",
                    audio.getMediaId(),
                    audioId,
                    timings.size()
            );

        } catch (Exception e) {

            log.error(
                    "Forced alignment failed: audioId={}",
                    audioId,
                    e
            );

            markFailedIfCurrent(
                    audioId,
                    expectedContentHash,
                    e
            );
        }
    }

    private void markFailedIfCurrent(
            Long audioId,
            String expectedContentHash,
            Exception exception
    ) {
        repository.findById(audioId)
                .filter(audio ->
                        expectedContentHash.equals(
                                audio.getContentHash()
                        )
                )
                .ifPresent(audio -> {

                    audio.setStatus(
                            StoryAudioStatus.FAILED
                    );

                    audio.setErrorMessage(
                            safeErrorMessage(
                                    exception
                            )
                    );

                    repository.save(audio);
                });
    }

    private String safeErrorMessage(
            Exception e
    ) {
        String message =
                e.getMessage();

        if (
                message == null ||
                        message.isBlank()
        ) {
            return "Story audio operation failed";
        }

        return message.length() > 500
                ? message.substring(
                0,
                500
        )
                : message;
    }
}