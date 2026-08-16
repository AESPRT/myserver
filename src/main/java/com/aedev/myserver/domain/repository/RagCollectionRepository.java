package com.aedev.myserver.domain.repository;

import com.aedev.myserver.domain.entity.RagCollection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RagCollectionRepository extends JpaRepository<RagCollection, Long> {
    Optional<RagCollection> findBySlug(String slug);
    boolean existsBySlug(String slug);
}