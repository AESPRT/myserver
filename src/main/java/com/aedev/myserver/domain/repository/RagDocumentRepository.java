package com.aedev.myserver.domain.repository;

import com.aedev.myserver.domain.entity.RagDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RagDocumentRepository extends JpaRepository<RagDocument, Long> {
    Optional<RagDocument> findByCollectionIdAndExternalId(Long collectionId, String externalId);
}