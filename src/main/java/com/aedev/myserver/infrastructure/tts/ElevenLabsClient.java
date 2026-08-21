package com.aedev.myserver.infrastructure.tts;

import com.aedev.myserver.application.dto.audio.WordTiming;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
public class ElevenLabsClient {

    private final WebClient elevenLabsWebClient;
    private final ElevenLabsProperties properties;

    public ElevenLabsClient(
            @Qualifier("elevenLabsWebClient")
            WebClient elevenLabsWebClient,
            ElevenLabsProperties properties
    ) {
        this.elevenLabsWebClient = elevenLabsWebClient;
        this.properties = properties;
    }

    /**
     * Generate completely new speech AND obtain timing metadata.
     * <p>
     * Use this only when:
     * - audio does not exist, or
     * - transcript has changed.
     */
    public TtsSynthesisResult synthesize(String transcript) {
        try {

            ElevenLabsTtsResponse response =
                    elevenLabsWebClient.post()
                            .uri(
                                    "/v1/text-to-speech/{voiceId}/with-timestamps",
                                    properties.voiceId()
                            )
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .bodyValue(
                                    new TtsRequestBody(
                                            transcript,
                                            properties.modelId()
                                    )
                            )
                            .retrieve()
                            .bodyToMono(ElevenLabsTtsResponse.class)
                            .block();

            if (response == null) {
                throw new IllegalStateException(
                        "ElevenLabs returned an empty response"
                );
            }

            if (
                    response.audioBase64() == null ||
                            response.audioBase64().isBlank()
            ) {
                throw new IllegalStateException(
                        "ElevenLabs response did not contain audio"
                );
            }

            byte[] audioBytes;

            try {
                audioBytes = Base64.getDecoder()
                        .decode(response.audioBase64());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "Failed to decode ElevenLabs audio",
                        e
                );
            }

            /*
             * alignment corresponds to the original transcript.
             *
             * That is preferable for the Android UI because Android
             * displays the original transcript.
             */
            List<WordTiming> wordTimings =
                    extractWordTimings(
                            response.alignment()
                    );

            return new TtsSynthesisResult(
                    audioBytes,
                    wordTimings
            );

        } catch (WebClientResponseException.TooManyRequests e) {

            throw new ElevenLabsIntegrationException(
                    "ElevenLabs rate limit exceeded",
                    e.getStatusCode(),
                    e
            );

        } catch (WebClientResponseException e) {

            throw new ElevenLabsIntegrationException(
                    buildElevenLabsErrorMessage(e),
                    e.getStatusCode(),
                    e
            );
        }
    }

    /**
     * Generate ONLY word timing metadata for an already-generated MP3.
     * <p>
     * This does NOT regenerate the TTS audio.
     * <p>
     * Existing audio:
     * <p>
     * MP3 + transcript
     *       ↓
     * ElevenLabs Forced Alignment
     *       ↓
     * WordTiming[]
     */
    public List<WordTiming> alignExistingAudio(
            String filePath,
            String transcript
    ) {
        try {

            if (filePath == null || filePath.isBlank()) {
                throw new IllegalArgumentException(
                        "Audio file path cannot be empty"
                );
            }

            if (transcript == null || transcript.isBlank()) {
                throw new IllegalArgumentException(
                        "Transcript cannot be empty"
                );
            }

            Path path = Path.of(filePath);

            if (!Files.exists(path)) {
                throw new IllegalStateException(
                        "Audio file does not exist: " + filePath
                );
            }

            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException(
                        "Audio path is not a file: " + filePath
                );
            }

            MultipartBodyBuilder multipart =
                    new MultipartBodyBuilder();

            multipart.part(
                    "file",
                    new FileSystemResource(path)
            );

            multipart.part(
                    "text",
                    transcript
            );

            ForcedAlignmentResponse response =
                    elevenLabsWebClient.post()
                            .uri("/v1/forced-alignment")
                            .contentType(
                                    MediaType.MULTIPART_FORM_DATA
                            )
                            .accept(
                                    MediaType.APPLICATION_JSON
                            )
                            .body(
                                    BodyInserters.fromMultipartData(
                                            multipart.build()
                                    )
                            )
                            .retrieve()
                            .bodyToMono(
                                    ForcedAlignmentResponse.class
                            )
                            .block();

            if (response == null) {
                throw new IllegalStateException(
                        "ElevenLabs forced alignment returned an empty response"
                );
            }

            if (
                    response.words() == null ||
                            response.words().isEmpty()
            ) {
                throw new IllegalStateException(
                        "ElevenLabs forced alignment returned no word timings"
                );
            }

            return response.words()
                    .stream()
                    .filter(word ->
                            word != null &&
                                    word.text() != null &&
                                    !word.text().isBlank() &&
                                    word.start() != null &&
                                    word.end() != null
                    )
                    .map(word ->
                            new WordTiming(
                                    word.text(),
                                    secondsToMillis(
                                            word.start()
                                    ),
                                    secondsToMillis(
                                            word.end()
                                    )
                            )
                    )
                    .toList();

        } catch (WebClientResponseException.TooManyRequests e) {

            throw new ElevenLabsIntegrationException(
                    "ElevenLabs forced alignment rate limit exceeded",
                    e.getStatusCode(),
                    e
            );

        } catch (WebClientResponseException e) {

            throw new ElevenLabsIntegrationException(
                    buildElevenLabsErrorMessage(e),
                    e.getStatusCode(),
                    e
            );
        }
    }

    /**
     * Convert character-level timestamps returned by
     * /with-timestamps into word-level timestamps.
     */
    private List<WordTiming> extractWordTimings(
            ElevenLabsTtsResponse.Alignment alignment
    ) {
        if (alignment == null) {
            return List.of();
        }

        List<String> characters =
                alignment.characters();

        List<Double> starts =
                alignment.characterStartTimesSeconds();

        List<Double> ends =
                alignment.characterEndTimesSeconds();

        if (
                characters == null ||
                        starts == null ||
                        ends == null ||
                        characters.isEmpty()
        ) {
            return List.of();
        }

        int count = Math.min(
                characters.size(),
                Math.min(
                        starts.size(),
                        ends.size()
                )
        );

        List<WordTiming> words =
                new ArrayList<>();

        StringBuilder currentWord =
                new StringBuilder();

        Double wordStartSeconds = null;
        Double wordEndSeconds = null;

        for (int i = 0; i < count; i++) {

            String character =
                    characters.get(i);

            if (
                    character == null ||
                            character.isEmpty()
            ) {
                continue;
            }

            Double startSeconds =
                    starts.get(i);

            Double endSeconds =
                    ends.get(i);

            if (
                    startSeconds == null ||
                            endSeconds == null
            ) {
                continue;
            }

            boolean whitespace =
                    character
                            .chars()
                            .allMatch(
                                    Character::isWhitespace
                            );

            if (whitespace) {

                addWord(
                        words,
                        currentWord,
                        wordStartSeconds,
                        wordEndSeconds
                );

                currentWord.setLength(0);

                wordStartSeconds = null;
                wordEndSeconds = null;

                continue;
            }

            if (currentWord.isEmpty()) {
                wordStartSeconds =
                        startSeconds;
            }

            currentWord.append(character);

            wordEndSeconds =
                    endSeconds;
        }

        // Flush final word
        addWord(
                words,
                currentWord,
                wordStartSeconds,
                wordEndSeconds
        );

        return List.copyOf(words);
    }

    private void addWord(
            List<WordTiming> words,
            StringBuilder text,
            Double startSeconds,
            Double endSeconds
    ) {
        if (
                text.isEmpty() ||
                        startSeconds == null ||
                        endSeconds == null
        ) {
            return;
        }

        words.add(
                new WordTiming(
                        text.toString(),
                        secondsToMillis(
                                startSeconds
                        ),
                        secondsToMillis(
                                endSeconds
                        )
                )
        );
    }

    private long secondsToMillis(
            double seconds
    ) {
        return Math.round(
                seconds * 1000.0
        );
    }

    private String buildElevenLabsErrorMessage(
            WebClientResponseException e
    ) {
        String body =
                e.getResponseBodyAsString();

        if (
                !body.isBlank()
        ) {
            return "ElevenLabs request failed: "
                    + e.getStatusCode()
                    + " - "
                    + body;
        }

        return "ElevenLabs request failed: "
                + e.getStatusCode()
                + " "
                + e.getStatusText();
    }

    private record TtsRequestBody(
            String text,
            String model_id
    ) {
    }
}