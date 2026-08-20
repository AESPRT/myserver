package com.aedev.myserver.application.service.audio;

import com.aedev.myserver.application.dto.audio.GenerateStoryAudioRequest;
import com.aedev.myserver.application.dto.audio.StoryAudioResponse;
import com.aedev.myserver.application.exception.AudioFileMissingException;
import com.aedev.myserver.domain.entity.StoryAudio;
import com.aedev.myserver.domain.repository.StoryAudioRepository;
import com.aedev.myserver.infrastructure.audio.AudioFileStorageService;
import com.aedev.myserver.infrastructure.tts.ElevenLabsClient;
import com.aedev.myserver.infrastructure.tts.ElevenLabsProperties;
import com.aedev.myserver.infrastructure.tts.TtsSynthesisResult;
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
    private final ElevenLabsClient elevenLabsClient;
    private final ElevenLabsProperties elevenLabsProperties;
    private final AudioFileStorageService storageService;

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
            ElevenLabsClient elevenLabsClient,
            ElevenLabsProperties elevenLabsProperties,
            AudioFileStorageService storageService
    ) {
        this.storyAudioRepository = storyAudioRepository;
        this.elevenLabsClient = elevenLabsClient;
        this.elevenLabsProperties = elevenLabsProperties;
        this.storageService = storageService;
    }

    public StoryAudioResponse generateOrGetAudio(GenerateStoryAudioRequest request) {
        ReentrantLock lock = mediaLocks.computeIfAbsent(request.mediaId(), id -> new ReentrantLock());
        lock.lock();
        try {
            return doGenerateOrGetAudio(request);
        } finally {
            lock.unlock();
            // Avoid unbounded growth of the lock map across the app's
            // lifetime -- safe to remove once no thread holds it, since
            // we're inside the lock's own finally block here.
            mediaLocks.remove(request.mediaId(), lock);
        }
    }

    private StoryAudioResponse doGenerateOrGetAudio(GenerateStoryAudioRequest request) {
        String contentHash =
                sha256(request.transcript());

        Optional<StoryAudio> existing =
                storyAudioRepository.findByMediaId(
                        request.mediaId()
                );

        if (existing.isPresent()) {
            StoryAudio audio = existing.get();

            if (
                    audio.getContentHash()
                            .equals(contentHash)
            ) {
                if (
                        !storageService.exists(
                                audio.getFilePath()
                        )
                ) {
                    throw new AudioFileMissingException(
                            request.mediaId()
                    );
                }

                boolean hasWordTimings =
                        audio.getWordTimings() != null &&
                                !audio.getWordTimings().isEmpty();

                if (hasWordTimings) {

                    log.info(
                            "Story audio cache hit: mediaId={}, audioId={}, words={}",
                            request.mediaId(),
                            audio.getId(),
                            audio.getWordTimings().size()
                    );

                    return StoryAudioResponse.from(audio);
                }

                /*
                 * Old cached audio generated before timestamp
                 * support was introduced.
                 */
                log.info(
                        "Cached audio missing timestamps. Regenerating: mediaId={}, audioId={}",
                        request.mediaId(),
                        audio.getId()
                );

                return regenerate(
                        audio,
                        request,
                        contentHash
                );
            }

            log.info(
                    "Transcript changed for mediaId={}, regenerating audio",
                    request.mediaId()
            );

            return regenerate(
                    audio,
                    request,
                    contentHash
            );
        }

        return generateNew(
                request,
                contentHash
        );
    }

    private StoryAudioResponse generateNew(GenerateStoryAudioRequest request, String contentHash) {
        log.info("Generating story audio: mediaId={}, characters={}", request.mediaId(), request.transcript().length());

        TtsSynthesisResult result =
                elevenLabsClient.synthesize(request.transcript());
        String fileName = request.mediaId() + "-" + contentHash.substring(0, 8) + ".mp3";
        AudioFileStorageService.StoredFile stored = storageService.store(fileName, result.audioBytes());

        StoryAudio audio = StoryAudio.builder()
                .mediaId(request.mediaId())
                .title(request.title())
                .fileName(stored.fileName())
                .filePath(stored.filePath())
                .url(stored.url())
                .voiceId(elevenLabsProperties.voiceId())
                .modelId(elevenLabsProperties.modelId())
                .characterCount(request.transcript().length())
                .fileSize(stored.fileSize())
                .contentHash(contentHash)
                .wordTimings(result.wordTimings())
                .build();

        StoryAudio saved = storyAudioRepository.save(audio);
        return StoryAudioResponse.from(saved);
    }

    private StoryAudioResponse regenerate(StoryAudio existing, GenerateStoryAudioRequest request, String contentHash) {
        TtsSynthesisResult result =
                elevenLabsClient.synthesize(request.transcript());
        String fileName = request.mediaId() + "-" + contentHash.substring(0, 8) + ".mp3";
        AudioFileStorageService.StoredFile stored = storageService.store(fileName, result.audioBytes());

        existing.setTitle(request.title());
        existing.setFileName(stored.fileName());
        existing.setFilePath(stored.filePath());
        existing.setUrl(stored.url());
        existing.setCharacterCount(request.transcript().length());
        existing.setFileSize(stored.fileSize());
        existing.setContentHash(contentHash);
        existing.setWordTimings(result.wordTimings());

        StoryAudio saved = storyAudioRepository.save(existing);
        return StoryAudioResponse.from(saved);
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
}