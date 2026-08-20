package com.aedev.myserver.infrastructure.tts;

import com.aedev.myserver.application.dto.audio.WordTiming;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

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

    public TtsSynthesisResult synthesize(String transcript) {
        try {
            ElevenLabsTtsResponse response = elevenLabsWebClient.post()
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

            byte[] audioBytes = Base64.getDecoder()
                    .decode(response.audioBase64());

            /*
             * Use alignment rather than normalizedAlignment.
             *
             * alignment corresponds to the original supplied transcript,
             * which is what the Android UI will display.
             */
            List<WordTiming> wordTimings =
                    extractWordTimings(response.alignment());

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
                    "ElevenLabs request failed: "
                            + e.getStatusCode()
                            + " "
                            + e.getStatusText(),
                    e.getStatusCode(),
                    e
            );

        } catch (IllegalArgumentException e) {

            throw new IllegalStateException(
                    "Failed to decode ElevenLabs audio",
                    e
            );
        }
    }

    private List<WordTiming> extractWordTimings(
            ElevenLabsTtsResponse.Alignment alignment
    ) {
        if (alignment == null) {
            return List.of();
        }

        List<String> characters = alignment.characters();
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
                Math.min(starts.size(), ends.size())
        );

        List<WordTiming> words = new ArrayList<>();

        StringBuilder currentWord = new StringBuilder();

        Double wordStartSeconds = null;
        Double wordEndSeconds = null;

        for (int i = 0; i < count; i++) {
            String character = characters.get(i);

            if (character == null || character.isEmpty()) {
                continue;
            }

            double startSeconds = starts.get(i);
            double endSeconds = ends.get(i);

            boolean whitespace =
                    character.chars().allMatch(Character::isWhitespace);

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
                wordStartSeconds = startSeconds;
            }

            currentWord.append(character);
            wordEndSeconds = endSeconds;
        }

        // Flush final word.
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
                        secondsToMillis(startSeconds),
                        secondsToMillis(endSeconds)
                )
        );
    }

    private long secondsToMillis(double seconds) {
        return Math.round(seconds * 1000.0);
    }

    private record TtsRequestBody(
            String text,
            String model_id
    ) {
    }
}