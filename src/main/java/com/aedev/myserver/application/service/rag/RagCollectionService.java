package com.aedev.myserver.application.service.rag;

import com.aedev.myserver.application.dto.rag.CreateCollectionRequest;
import com.aedev.myserver.domain.entity.RagCollection;
import com.aedev.myserver.domain.repository.RagCollectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RagCollectionService {

    private final RagCollectionRepository collectionRepository;

    public RagCollectionService(
            RagCollectionRepository collectionRepository
    ) {
        this.collectionRepository = collectionRepository;
    }

    @Transactional
    public RagCollection create(CreateCollectionRequest request) {

        if (collectionRepository.findBySlug(request.slug()).isPresent()) {
            throw new IllegalArgumentException(
                    "RAG collection already exists: " + request.slug()
            );
        }

        RagCollection collection = RagCollection.builder()
                .slug(request.slug())
                .name(request.name())
                .description(request.description())
                .build();

        return collectionRepository.save(collection);
    }
}