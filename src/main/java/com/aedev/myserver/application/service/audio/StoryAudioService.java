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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class StoryAudioService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    StoryAudioService.class
            );

    private final StoryAudioRepository storyAudioRepository;
    private final ElevenLabsProperties elevenLabsProperties;
    private final AudioFileStorageService storageService;
    private final StoryAudioGenerationService generationService;

    /**
     * One in-process lock per mediaId.
     * <p>
     * Do NOT remove locks from this map immediately after
     * unlocking; another thread may already be waiting on
     * that same lock.
     * <p>
     * For multiple backend instances, replace this with
     * a distributed/DB lock later.
     */
    private final ConcurrentHashMap<String, ReentrantLock>
            mediaLocks =
            new ConcurrentHashMap<>();

    public StoryAudioService(
            StoryAudioRepository storyAudioRepository,
            ElevenLabsProperties elevenLabsProperties,
            AudioFileStorageService storageService,
            StoryAudioGenerationService generationService
    ) {
        this.storyAudioRepository =
                storyAudioRepository;

        this.elevenLabsProperties =
                elevenLabsProperties;

        this.storageService =
                storageService;

        this.generationService =
                generationService;
    }

    public StoryAudioResponse generateOrGetAudio(
            GenerateStoryAudioRequest request
    ) {

        ReentrantLock lock =
                mediaLocks.computeIfAbsent(
                        request.mediaId(),
                        id -> new ReentrantLock()
                );

        lock.lock();

        try {
            return doGenerateOrGetAudio(
                    request
            );
        } finally {
            lock.unlock();
        }
    }

    private StoryAudioResponse doGenerateOrGetAudio(
            GenerateStoryAudioRequest request
    ) {

        String contentHash =
                sha256(
                        request.transcript()
                );

        Optional<StoryAudio> existing =
                storyAudioRepository
                        .findByMediaId(
                                request.mediaId()
                        );

        /*
         * =================================================
         * EXISTING DATABASE RECORD
         * =================================================
         */
        if (existing.isPresent()) {

            StoryAudio audio =
                    existing.get();

            boolean sameContent =
                    contentHash.equals(
                            audio.getContentHash()
                    );

            /*
             * =================================================
             * SAME TRANSCRIPT
             * =================================================
             */
            if (sameContent) {

                boolean fileExists =
                        audio.getFilePath() != null &&
                                !audio.getFilePath().isBlank() &&
                                storageService.exists(
                                        audio.getFilePath()
                                );

                boolean hasWordTimings =
                        audio.getWordTimings() != null &&
                                !audio.getWordTimings().isEmpty();

                /*
                 * ---------------------------------------------
                 * CASE 1
                 *
                 * Complete cache hit.
                 *
                 * MP3 exists.
                 * Timings exist.
                 *
                 * Spend zero new generation work.
                 * ---------------------------------------------
                 */
                if (
                        audio.getStatus()
                                == StoryAudioStatus.READY &&
                                fileExists &&
                                hasWordTimings
                ) {

                    log.info(
                            "Story cache hit: mediaId={}, audioId={}, words={}",
                            request.mediaId(),
                            audio.getId(),
                            audio.getWordTimings().size()
                    );

                    return StoryAudioResponse.from(
                            audio
                    );
                }

                /*
                 * ---------------------------------------------
                 * CASE 2
                 *
                 * A background job is already running.
                 * ---------------------------------------------
                 */
                if (
                        audio.getStatus()
                                == StoryAudioStatus.PROCESSING
                ) {

                    log.info(
                            "Story operation already processing: mediaId={}, audioId={}",
                            request.mediaId(),
                            audio.getId()
                    );

                    return StoryAudioResponse.from(
                            audio
                    );
                }

                /*
                 * ---------------------------------------------
                 * CASE 3
                 *
                 * MP3 EXISTS
                 * WORDS MISSING
                 *
                 * DO NOT regenerate TTS.
                 *
                 * Run Forced Alignment on existing MP3 only.
                 * ---------------------------------------------
                 */
                if (
                        fileExists &&
                                !hasWordTimings
                ) {

                    log.info(
                            "Existing MP3 has no word timings. Starting forced alignment only: mediaId={}, audioId={}, file={}",
                            request.mediaId(),
                            audio.getId(),
                            audio.getFileName()
                    );

                    /*
                     * IMPORTANT:
                     *
                     * Do NOT call prepareForFullGeneration().
                     *
                     * It would clear the existing MP3 fields.
                     */
                    audio.setStatus(
                            StoryAudioStatus.PROCESSING
                    );

                    audio.setErrorMessage(null);

                    StoryAudio saved =
                            storyAudioRepository.save(
                                    audio
                            );

                    generationService
                            .generateWordTimingsOnly(
                                    saved.getId(),
                                    request.transcript(),
                                    contentHash
                            );

                    /*
                     * URL / fileName / filePath / fileSize
                     * are still preserved.
                     */
                    return StoryAudioResponse.from(
                            saved
                    );
                }

                /*
                 * ---------------------------------------------
                 * CASE 4
                 *
                 * Transcript is unchanged,
                 * but actual MP3 is missing.
                 *
                 * Full TTS generation is unavoidable.
                 * ---------------------------------------------
                 */
                log.warn(
                        "Story MP3 is missing. Full regeneration required: mediaId={}, audioId={}",
                        request.mediaId(),
                        audio.getId()
                );

                prepareForFullGeneration(
                        audio,
                        request,
                        contentHash
                );

                StoryAudio saved =
                        storyAudioRepository.save(
                                audio
                        );

                generationService.generate(
                        saved.getId(),
                        request,
                        contentHash
                );

                return StoryAudioResponse.from(
                        saved
                );
            }

            /*
             * =================================================
             * TRANSCRIPT CHANGED
             *
             * Existing MP3 is no longer valid.
             *
             * Generate new audio + timestamps.
             * =================================================
             */

            log.info(
                    "Transcript changed. Full story regeneration required: mediaId={}, audioId={}",
                    request.mediaId(),
                    audio.getId()
            );

            prepareForFullGeneration(
                    audio,
                    request,
                    contentHash
            );

            StoryAudio saved =
                    storyAudioRepository.save(
                            audio
                    );

            generationService.generate(
                    saved.getId(),
                    request,
                    contentHash
            );

            return StoryAudioResponse.from(
                    saved
            );
        }

        /*
         * =================================================
         * BRAND-NEW STORY
         * =================================================
         */

        log.info(
                "Creating new story generation record: mediaId={}",
                request.mediaId()
        );

        StoryAudio audio =
                StoryAudio.builder()
                        .mediaId(
                                request.mediaId()
                        )
                        .title(
                                request.title()
                        )
                        .voiceId(
                                elevenLabsProperties.voiceId()
                        )
                        .modelId(
                                elevenLabsProperties.modelId()
                        )
                        .characterCount(
                                request.transcript().length()
                        )
                        .contentHash(
                                contentHash
                        )
                        .status(
                                StoryAudioStatus.PROCESSING
                        )
                        .wordTimings(
                                List.of()
                        )
                        .build();

        StoryAudio saved =
                storyAudioRepository.save(
                        audio
                );

        generationService.generate(
                saved.getId(),
                request,
                contentHash
        );

        return StoryAudioResponse.from(
                saved
        );
    }

    /**
     * ONLY use this when a new MP3 must actually be generated.
     * <p>
     * This intentionally clears the previous audio output.
     */
    private void prepareForFullGeneration(
            StoryAudio audio,
            GenerateStoryAudioRequest request,
            String contentHash
    ) {
        audio.setTitle(
                request.title()
        );

        audio.setVoiceId(
                elevenLabsProperties.voiceId()
        );

        audio.setModelId(
                elevenLabsProperties.modelId()
        );

        audio.setCharacterCount(
                request.transcript().length()
        );

        audio.setContentHash(
                contentHash
        );

        audio.setStatus(
                StoryAudioStatus.PROCESSING
        );

        audio.setErrorMessage(null);

        audio.setWordTimings(
                java.util.List.of()
        );

        /*
         * IMPORTANT:
         *
         * DO NOT clear:
         *
         * fileName
         * filePath
         * url
         * fileSize
         *
         * The database columns are NOT NULL and the old
         * audio should remain available until the new one
         * has successfully generated.
         */
    }

    private String sha256(
            String input
    ) {
        try {

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash =
                    digest.digest(
                            input.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            return HexFormat.of()
                    .formatHex(
                            hash
                    );

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Failed to hash transcript",
                    e
            );
        }
    }

    public StoryAudioResponse getByMediaId(
            String mediaId
    ) {

        StoryAudio audio =
                storyAudioRepository
                        .findByMediaId(
                                mediaId
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Story audio not found: "
                                                        + mediaId
                                        )
                        );

        return StoryAudioResponse.from(
                audio
        );
    }
}