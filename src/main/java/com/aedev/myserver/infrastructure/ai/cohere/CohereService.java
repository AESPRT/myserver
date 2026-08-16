package com.aedev.myserver.infrastructure.ai.cohere;

import com.aedev.myserver.application.exception.CohereIntegrationException;
import com.aedev.myserver.infrastructure.ai.cohere.dto.CohereChatRequest;
import com.aedev.myserver.infrastructure.ai.cohere.dto.CohereChatResponse;
import com.aedev.myserver.infrastructure.ai.cohere.dto.CohereEmbedRequest;
import com.aedev.myserver.infrastructure.ai.cohere.dto.CohereEmbedResponse;
import com.aedev.myserver.infrastructure.config.CohereProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;

import static com.aedev.myserver.infrastructure.config.CohereClientConfig.COHERE_WEB_CLIENT;

/**
 * Dedicated Cohere integration service. The only class that talks to the
 * Cohere API -- business services (RagIngestionService,
 * RagQueryService) never call Cohere directly, same principle as
 * PayMongoService/SemaphoreService.
 */
@Service
public class CohereService {

    private static final Logger log = LoggerFactory.getLogger(CohereService.class);
    private static final String EMBED_PATH = "/v2/embed";
    private static final String CHAT_PATH = "/v2/chat";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Cohere's embed endpoint accepts up to 96 texts per request. Batched
     * client-side so ingesting a large document doesn't require the
     * caller to chunk requests themselves.
     */
    private static final int MAX_TEXTS_PER_EMBED_REQUEST = 96;

    private final WebClient cohereWebClient;
    private final CohereProperties properties;

    public CohereService(
            @Qualifier(COHERE_WEB_CLIENT) WebClient cohereWebClient,
            CohereProperties properties
    ) {
        this.cohereWebClient = cohereWebClient;
        this.properties = properties;
    }

    /**
     * Embeds a batch of texts for storage (input_type=search_document).
     * Returns embeddings in the same order as the input texts.
     */
    public List<float[]> embedDocuments(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }

        List<float[]> allEmbeddings = new java.util.ArrayList<>();
        for (int i = 0; i < texts.size(); i += MAX_TEXTS_PER_EMBED_REQUEST) {
            List<String> batch = texts.subList(i, Math.min(i + MAX_TEXTS_PER_EMBED_REQUEST, texts.size()));
            CohereEmbedRequest request = CohereEmbedRequest.forDocuments(properties.embedModel(), batch);
            allEmbeddings.addAll(callEmbed(request));
        }
        return allEmbeddings;
    }

    /**
     * Embeds a single query string for retrieval (input_type=search_query).
     * Deliberately a separate method from embedDocuments, not an
     * overload with a flag -- input_type asymmetry is a real modeling
     * detail, not an implementation nuance, and callers should not be
     * able to accidentally pass the wrong one via a boolean.
     */
    public float[] embedQuery(String query) {
        CohereEmbedRequest request = CohereEmbedRequest.forQuery(properties.embedModel(), query);
        List<float[]> result = callEmbed(request);
        if (result.isEmpty()) {
            throw new CohereIntegrationException("Cohere returned no embedding for query");
        }
        return result.getFirst();
    }

    private List<float[]> callEmbed(CohereEmbedRequest request) {
        CohereEmbedResponse response;
        try {
            response = cohereWebClient.post()
                    .uri(EMBED_PATH)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(CohereEmbedResponse.class)
                    .block(REQUEST_TIMEOUT);
        } catch (WebClientResponseException e) {
            log.error("Cohere embed API returned {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new CohereIntegrationException("Embed request failed: " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Cohere embed API call failed", e);
            throw new CohereIntegrationException("Embed request failed", e);
        }

        if (response == null || response.embeddings() == null || response.embeddings().floatEmbeddings() == null) {
            throw new CohereIntegrationException("Cohere returned an empty embed response");
        }

        return response.embeddings().floatEmbeddings().stream()
                .map(this::toFloatArray)
                .toList();
    }

    private float[] toFloatArray(List<Double> doubles) {
        float[] result = new float[doubles.size()];
        for (int i = 0; i < doubles.size(); i++) {
            result[i] = doubles.get(i).floatValue();
        }
        return result;
    }

    /**
     * Sends a RAG-grounded chat request. Cohere natively accepts
     * retrieved chunks via the "documents" field and returns citations
     * mapping response spans back to document ids -- callers pass the
     * chunk identifiers they used as document ids so citations can be
     * traced back to the originating rag_chunks row.
     */
    public CohereChatResponse chatWithDocuments(
            String userMessage,
            List<CohereChatRequest.CohereRagDocument> documents
    ) {
        CohereChatRequest request = new CohereChatRequest(
                properties.chatModel(),
                List.of(CohereChatRequest.CohereChatMessage.user(userMessage)),
                documents,
                0.3 // low temperature: favor grounded, consistent answers over creative ones
        );

        try {
            CohereChatResponse response = cohereWebClient.post()
                    .uri(CHAT_PATH)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(CohereChatResponse.class)
                    .block(REQUEST_TIMEOUT);

            if (response == null || response.message() == null) {
                throw new CohereIntegrationException("Cohere returned an empty chat response");
            }
            return response;
        } catch (WebClientResponseException e) {
            log.error("Cohere chat API returned {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new CohereIntegrationException("Chat request failed: " + e.getStatusCode(), e);
        } catch (CohereIntegrationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Cohere chat API call failed", e);
            throw new CohereIntegrationException("Chat request failed", e);
        }
    }
}