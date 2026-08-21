package com.aedev.myserver.application.service.audio;

import com.aedev.myserver.application.dto.audio.GenerateStoryAudioRequest;
import com.aedev.myserver.application.dto.audio.StoryAudioResponse;
import com.aedev.myserver.domain.entity.StoryAudio;
import com.aedev.myserver.domain.enums.StoryAudioStatus;
import com.aedev.myserver.domain.repository.StoryAudioRepository;
import com.aedev.myserver.infrastructure.audio.AudioFileStorageService;
import com.aedev.myserver.infrastructure.tts.ElevenLabsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class StoryAudioService {

    private static final Logger log = LoggerFactory.getLogger(StoryAudioService.class);

    private final StoryAudioRepository storyAudioRepository;
    private final ElevenLabsProperties elevenLabsProperties;
    private final AudioFileStorageService storageService;
    private final StoryAudioGenerationService generationService;

    // Per-mediaId in-process lock. This solves the "two concurrent
    // requests for the same new mediaId both miss the DB check and both
    // call ElevenLabs" race from section 10 of the plan -- but ONLY for a
    // single backend instance. If this app is ever horizontally scaled to
    // multiple instances, this map-based lock does nothing across
    // processes and a distributed lock (DB advisory lock, Redis, etc.)
    // would be needed instead. Flagging this now since it's an easy trap
    // to hit later if the app scales without anyone remembering this.
    private final ConcurrentHashMap<String, ReentrantLock> mediaLocks = new ConcurrentHashMap<>();

    public StoryAudioService(
            StoryAudioRepository storyAudioRepository,
            ElevenLabsProperties elevenLabsProperties,
            AudioFileStorageService storageService,
            StoryAudioGenerationService generationService
    ) {
        this.storyAudioRepository = storyAudioRepository;
        this.elevenLabsProperties = elevenLabsProperties;
        this.storageService = storageService;
        this.generationService = generationService;
    }

    public StoryAudioResponse generateOrGetAudio(GenerateStoryAudioRequest request) {
        ReentrantLock lock = mediaLocks.computeIfAbsent(
                request.mediaId(),
                id -> new ReentrantLock()
        );

        lock.lock();

        try {
            return doGenerateOrGetAudio(request);
        } finally {
            lock.unlock();
        }
    }

    private StoryAudioResponse doGenerateOrGetAudio(
            GenerateStoryAudioRequest request
    ) {
        String contentHash =
                sha256(request.transcript());

        Optional<StoryAudio> existing =
                storyAudioRepository.findByMediaId(
                        request.mediaId()
                );

        if (existing.isPresent()) {
            StoryAudio audio = existing.get();

            boolean sameContent =
                    contentHash.equals(
                            audio.getContentHash()
                    );

            if (sameContent) {

                if (
                        audio.getStatus()
                                == StoryAudioStatus.PROCESSING
                ) {
                    return StoryAudioResponse.from(audio);
                }

                if (
                        audio.getStatus()
                                == StoryAudioStatus.READY
                ) {
                    boolean fileExists =
                            audio.getFilePath() != null &&
                                    storageService.exists(
                                            audio.getFilePath()
                                    );

                    boolean hasWordTimings =
                            audio.getWordTimings() != null &&
                                    !audio.getWordTimings().isEmpty();

                    if (fileExists && hasWordTimings) {
                        return StoryAudioResponse.from(audio);
                    }
                }

                /*
                 * Existing legacy audio, FAILED generation,
                 * missing file, or missing timestamp data.
                 *
                 * Queue generation again.
                 */
                prepareForGeneration(
                        audio,
                        request,
                        contentHash
                );

                StoryAudio saved =
                        storyAudioRepository.save(audio);

                generationService.generate(
                        saved.getId(),
                        request,
                        contentHash
                );

                return StoryAudioResponse.from(saved);
            }

            // Transcript changed.
            prepareForGeneration(
                    audio,
                    request,
                    contentHash
            );

            StoryAudio saved =
                    storyAudioRepository.save(audio);

            generationService.generate(
                    saved.getId(),
                    request,
                    contentHash
            );

            return StoryAudioResponse.from(saved);
        }

        StoryAudio audio = StoryAudio.builder()
                .mediaId(request.mediaId())
                .title(request.title())
                .voiceId(elevenLabsProperties.voiceId())
                .modelId(elevenLabsProperties.modelId())
                .characterCount(
                        request.transcript().length()
                )
                .contentHash(contentHash)
                .status(StoryAudioStatus.PROCESSING)
                .build();

        StoryAudio saved =
                storyAudioRepository.save(audio);

        generationService.generate(
                saved.getId(),
                request,
                contentHash
        );

        return StoryAudioResponse.from(saved);
    }

    private void prepareForGeneration(
            StoryAudio audio,
            GenerateStoryAudioRequest request,
            String contentHash
    ) {
        audio.setTitle(request.title());

        audio.setVoiceId(
                elevenLabsProperties.voiceId()
        );

        audio.setModelId(
                elevenLabsProperties.modelId()
        );

        audio.setCharacterCount(
                request.transcript().length()
        );

        audio.setContentHash(contentHash);

        audio.setStatus(
                StoryAudioStatus.PROCESSING
        );

        audio.setErrorMessage(null);

        audio.setWordTimings(
                java.util.List.of()
        );

        // Clear stale generated output
        audio.setUrl(null);
        audio.setFileName(null);
        audio.setFilePath(null);
        audio.setFileSize(null);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash transcript", e);
        }
    }

    public StoryAudioResponse getByMediaId(
            String mediaId
    ) {
        StoryAudio audio =
                storyAudioRepository
                        .findByMediaId(mediaId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Story audio not found: "
                                                + mediaId
                                )
                        );

        return StoryAudioResponse.from(audio);
    }
}