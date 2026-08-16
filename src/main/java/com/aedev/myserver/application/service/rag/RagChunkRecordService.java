package com.aedev.myserver.application.service.rag;

import com.aedev.myserver.application.dto.rag.IngestDocumentRequest;
import com.aedev.myserver.domain.entity.RagChunk;
import com.aedev.myserver.domain.entity.RagCollection;
import com.aedev.myserver.domain.entity.RagDocument;
import com.aedev.myserver.domain.repository.RagChunkRepository;
import com.aedev.myserver.domain.repository.RagDocumentRepository;
import com.pgvector.PGvector;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

/**
 * Owns writes to rag_documents / rag_chunks as one atomic operation.
 * Kept as a real separate bean from RagIngestionService (not a private
 * method there) so @Transactional is applied via Spring's proxy
 * correctly -- same-class method calls bypass the proxy and would
 * silently ignore @Transactional otherwise. Same pattern as
 * TransactionRecordService / SubscriptionRecordService elsewhere in
 * this codebase.
 */
@Service
public class RagChunkRecordService {

    private final RagDocumentRepository documentRepository;
    private final RagChunkRepository chunkRepository;

    public RagChunkRecordService(
            RagDocumentRepository documentRepository,
            RagChunkRepository chunkRepository
    ) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
    }

    @Transactional
    public RagDocument persist(
            RagCollection collection,
            IngestDocumentRequest request,
            List<String> chunkTexts,
            List<float[]> embeddings
    ) {
        RagDocument document = findExistingForUpsert(collection.getId(), request.externalId())
                .orElseGet(() -> RagDocument.builder()
                        .collection(collection)
                        .externalId(request.externalId())
                        .build());

        if (document.getId() != null) {
            // Re-ingestion: drop prior chunks before inserting the new set.
            chunkRepository.deleteByDocumentId(document.getId());
        }

        document.setTitle(request.title());
        document.setMetadata(request.metadata() != null ? request.metadata() : new HashMap<>());
        document = documentRepository.save(document);

        List<RagChunk> chunks = new ArrayList<>();

        for (int i = 0; i < chunkTexts.size(); i++) {
            RagChunk chunk = RagChunk.builder()
                    .document(document)
                    .collectionId(collection.getId())
                    .chunkIndex(i)
                    .content(chunkTexts.get(i))
                    .embedding(new PGvector(embeddings.get(i)))
                    .build();
            chunks.add(chunk);
        }

        chunkRepository.saveAll(chunks);

        return document;
    }

    private Optional<RagDocument> findExistingForUpsert(Long collectionId, String externalId) {
        if (externalId == null || externalId.isBlank()) {
            return Optional.empty();
        }
        return documentRepository.findByCollectionIdAndExternalId(collectionId, externalId);
    }
}