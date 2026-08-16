package com.aedev.myserver.presentation.controller.rag;

import com.aedev.myserver.application.dto.rag.CreateCollectionRequest;
import com.aedev.myserver.application.dto.rag.IngestDocumentRequest;
import com.aedev.myserver.application.dto.rag.RagQueryRequest;
import com.aedev.myserver.application.dto.rag.RagQueryResponse;
import com.aedev.myserver.application.service.rag.RagCollectionService;
import com.aedev.myserver.application.service.rag.RagIngestionService;
import com.aedev.myserver.application.service.rag.RagQueryService;
import com.aedev.myserver.domain.entity.RagCollection;
import com.aedev.myserver.domain.entity.RagDocument;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rag")
public class RagController {

    private final RagIngestionService ragIngestionService;
    private final RagQueryService ragQueryService;
    private final RagCollectionService ragCollectionService;

    public RagController(
            RagCollectionService ragCollectionService,
            RagIngestionService ragIngestionService,
            RagQueryService ragQueryService
    ) {
        this.ragCollectionService = ragCollectionService;
        this.ragIngestionService = ragIngestionService;
        this.ragQueryService = ragQueryService;
    }

    @PostMapping("/collections")
    public ResponseEntity<RagCollection> createCollection(
            @Valid @RequestBody CreateCollectionRequest request
    ) {
        RagCollection collection = ragCollectionService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(collection);
    }

    /**
     * Ingests or re-ingests a document into a RAG collection.
     * <p>
     * If externalId is provided and already exists in the collection,
     * the existing document's chunks are replaced.
     */
    @PostMapping("/{collectionSlug}/documents")
    public ResponseEntity<RagDocument> ingestDocument(
            @PathVariable String collectionSlug,
            @Valid @RequestBody IngestDocumentRequest request
    ) {
        RagDocument document = ragIngestionService.ingest(
                collectionSlug,
                request
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(document);
    }

    /**
     * Queries a RAG collection using semantic similarity search
     * followed by Cohere's grounded chat response.
     */
    @PostMapping("/{collectionSlug}/query")
    public ResponseEntity<RagQueryResponse> query(
            @PathVariable String collectionSlug,
            @Valid @RequestBody RagQueryRequest request
    ) {
        RagQueryResponse response = ragQueryService.query(
                collectionSlug,
                request
        );

        return ResponseEntity.ok(response);
    }
}
