package com.aedev.myserver.infrastructure.tts;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;

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

    public byte[] synthesize(String transcript) {
        try {
            Mono<byte[]> bodyMono = elevenLabsWebClient.post()
                    .uri("/v1/text-to-speech/{voiceId}", properties.voiceId())
                    .accept(MediaType.valueOf("audio/mpeg"))
                    .bodyValue(new TtsRequestBody(
                            transcript,
                            properties.modelId()
                    ))
                    .retrieve()
                    .bodyToFlux(DataBuffer.class)
                    .collect(
                            ByteArrayOutputStream::new,
                            (baos, buffer) -> {
                                byte[] bytes = new byte[buffer.readableByteCount()];
                                buffer.read(bytes);
                                DataBufferUtils.release(buffer);
                                baos.write(bytes, 0, bytes.length);
                            }
                    )
                    .map(ByteArrayOutputStream::toByteArray);

            return bodyMono.block();

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
        }
    }

    private record TtsRequestBody(
            String text,
            String model_id
    ) {}
}