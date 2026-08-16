package com.aedev.myserver.application.service.rag;

import com.aedev.myserver.application.dto.rag.IngestDocumentRequest;
import com.aedev.myserver.application.exception.RagCollectionNotFoundException;
import com.aedev.myserver.domain.entity.RagCollection;
import com.aedev.myserver.domain.entity.RagDocument;
import com.aedev.myserver.domain.repository.RagCollectionRepository;
import com.aedev.myserver.infrastructure.ai.cohere.CohereService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Owns the ingestion pipeline: chunk -> embed -> persist. Kept separate
 * from RagQueryService since ingestion and retrieval have different
 * transactional shapes and different external-call patterns (embedding
 * many chunks vs. embedding one query + one chat call).
 * <p>
 * The DB write is delegated to RagChunkRecordService (a separate bean,
 * not a private/protected method on this class) so @Transactional is
 * applied via Spring's proxy correctly -- same-class method calls
 * bypass the proxy and would silently skip the transaction boundary,
 * same rule already established in TransactionRecordService.
 */
@Service
public class RagIngestionService {

    private static final Logger log = LoggerFactory.getLogger(RagIngestionService.class);

    private final RagCollectionRepository collectionRepository;
    private final RagChunkRecordService ragChunkRecordService;
    private final CohereService cohereService;
    private final TextChunker textChunker;

    public RagIngestionService(
            RagCollectionRepository collectionRepository,
            RagChunkRecordService ragChunkRecordService,
            CohereService cohereService,
            TextChunker textChunker
    ) {
        this.collectionRepository = collectionRepository;
        this.ragChunkRecordService = ragChunkRecordService;
        this.cohereService = cohereService;
        this.textChunker = textChunker;
    }

    /**
     * Ingests a document into a collection. If externalId is provided
     * and a document with that externalId already exists in the
     * collection, its old chunks are deleted and replaced -- this is
     * the upsert path for "the source content changed, re-index it."
     * <p>
     * The Cohere embed call happens here, OUTSIDE any DB transaction, so
     * a slow or failing embed call never holds a DB connection/transaction
     * open. The DB write happens afterward, in RagChunkRecordService,
     * only once embeddings are already in hand.
     */
    public RagDocument ingest(String collectionSlug, IngestDocumentRequest request) {
        RagCollection collection = collectionRepository.findBySlug(collectionSlug)
                .orElseThrow(() -> new RagCollectionNotFoundException(collectionSlug));

        List<String> chunkTexts = textChunker.chunk(request.content());
        if (chunkTexts.isEmpty()) {
            throw new IllegalArgumentException("Document content produced no chunks");
        }

        List<float[]> embeddings = cohereService.embedDocuments(chunkTexts);

        RagDocument document = ragChunkRecordService.persist(collection, request, chunkTexts, embeddings);

        log.info("Ingested document {} into collection {} ({} chunks)",
                document.getId(), collection.getSlug(), chunkTexts.size());

        return document;
    }
}