package com.aedev.myserver.application.service.rag;

import com.aedev.myserver.application.dto.rag.RagQueryRequest;
import com.aedev.myserver.application.dto.rag.RagQueryResponse;
import com.aedev.myserver.application.exception.RagCollectionNotFoundException;
import com.aedev.myserver.domain.entity.RagChunk;
import com.aedev.myserver.domain.entity.RagCollection;
import com.aedev.myserver.domain.repository.RagChunkRepository;
import com.aedev.myserver.domain.repository.RagCollectionRepository;
import com.aedev.myserver.infrastructure.ai.cohere.CohereService;
import com.aedev.myserver.infrastructure.ai.cohere.dto.CohereChatRequest;
import com.aedev.myserver.infrastructure.ai.cohere.dto.CohereChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles RAG queries: embed the question, retrieve nearest chunks from
 * pgvector, hand them to Cohere's chat endpoint as grounding documents,
 * and map the response back with source attribution.
 * <p>
 * Read-only path -- no @Transactional needed since this only performs
 * reads (findNearest) plus external API calls, no writes.
 */
@Service
public class RagQueryService {

    private static final Logger log = LoggerFactory.getLogger(RagQueryService.class);

    private final RagCollectionRepository collectionRepository;
    private final RagChunkRepository chunkRepository;
    private final CohereService cohereService;

    public RagQueryService(
            RagCollectionRepository collectionRepository,
            RagChunkRepository chunkRepository,
            CohereService cohereService
    ) {
        this.collectionRepository = collectionRepository;
        this.chunkRepository = chunkRepository;
        this.cohereService = cohereService;
    }

    @Transactional(readOnly = true)
    public RagQueryResponse query(String collectionSlug, RagQueryRequest request) {
        RagCollection collection = collectionRepository.findBySlug(collectionSlug)
                .orElseThrow(() -> new RagCollectionNotFoundException(collectionSlug));

        float[] queryEmbedding = cohereService.embedQuery(request.question());

        List<RagChunk> nearestChunks = chunkRepository.findNearest(
                collection.getId(),
                toVectorLiteral(queryEmbedding),
                request.resolvedTopK()
        );

        if (nearestChunks.isEmpty()) {
            log.info("No chunks found in collection {} for query", collectionSlug);
            return new RagQueryResponse(
                    "I don't have any information to answer that yet.",
                    List.of()
            );
        }

        // Chunk id (as a String) is used as the Cohere document id so the
        // citation data Cohere returns can be traced back to a specific
        // rag_chunks row -- see the id -> chunk map built below.
        Map<String, RagChunk> chunksById = nearestChunks.stream()
                .collect(Collectors.toMap(c -> String.valueOf(c.getId()), c -> c));

        List<CohereChatRequest.CohereRagDocument> documents = nearestChunks.stream()
                .map(c -> CohereChatRequest.CohereRagDocument.of(String.valueOf(c.getId()), c.getContent()))
                .toList();

        CohereChatResponse response = cohereService.chatWithDocuments(request.question(), documents);

        String answer = extractAnswerText(response);
        List<RagQueryResponse.SourceChunk> sources = buildSources(response, chunksById, nearestChunks);

        return new RagQueryResponse(answer, sources);
    }

    private String extractAnswerText(CohereChatResponse response) {
        if (response.message() == null || response.message().content() == null) {
            return "";
        }
        return response.message().content().stream()
                .filter(block -> "text".equals(block.type()))
                .map(CohereChatResponse.ContentBlock::text)
                .collect(Collectors.joining());
    }

    /**
     * Builds the sources list from Cohere's citations when present,
     * falling back to "all retrieved chunks" if Cohere didn't return
     * citation data (some model/response configurations omit it). Either
     * way the caller gets to see what grounded the answer.
     */
    private List<RagQueryResponse.SourceChunk> buildSources(
            CohereChatResponse response,
            Map<String, RagChunk> chunksById,
            List<RagChunk> allRetrieved
    ) {
        if (response.citations() == null || response.citations().isEmpty()) {
            return allRetrieved.stream()
                    .map(this::toSourceChunk)
                    .toList();
        }

        return response.citations().stream()
                .flatMap(citation -> citation.sources() != null ? citation.sources().stream() : java.util.stream.Stream.empty())
                .map(CohereChatResponse.Source::id)
                .distinct()
                .map(chunksById::get)
                .filter(java.util.Objects::nonNull)
                .map(this::toSourceChunk)
                .toList();
    }

    private RagQueryResponse.SourceChunk toSourceChunk(RagChunk chunk) {
        return new RagQueryResponse.SourceChunk(
                chunk.getId(),
                chunk.getDocument().getId(),
                chunk.getDocument().getTitle(),
                chunk.getContent()
        );
    }

    /**
     * pgvector's JDBC binding for a plain native @Query parameter doesn't
     * accept a PGvector object directly -- it needs the literal string
     * form "[0.1,0.2,...]" that Postgres's vector input parser accepts,
     * which is then CAST to vector in the SQL itself (see
     * RagChunkRepository#findNearest).
     */
    private String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}