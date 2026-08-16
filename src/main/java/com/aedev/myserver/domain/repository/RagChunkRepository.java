package com.aedev.myserver.domain.repository;

import com.aedev.myserver.domain.entity.RagChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RagChunkRepository extends JpaRepository<RagChunk, Long> {

    void deleteByDocumentId(Long documentId);

    /**
     * Cosine-distance nearest-neighbor search scoped to one collection.
     * A native query is required because Spring Data JPA/HQL has no
     * concept of pgvector's "<=>" distance operator -- this is
     * pgvector-specific SQL, not portable to another DB without changes.
     * <p>
     * "<=>" returns cosine DISTANCE (0 = identical, 2 = opposite), not
     * similarity -- ORDER BY ascending distance is therefore "most
     * similar first", which is what callers actually want from a
     * ranked-results list.
     * <p>
     * :queryEmbedding is bound as a pgvector literal string
     * ("[0.1,0.2,...]") by RagQueryService rather than a raw float[],
     * since Hibernate's native query parameter binding doesn't know how
     * to serialize a PGvector object for a plain @Query -- see
     * RagQueryService#toVectorLiteral.
     */
    @Query(value = """
            SELECT * FROM rag_chunks
            WHERE collection_id = :collectionId
            ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<RagChunk> findNearest(
            @Param("collectionId") Long collectionId,
            @Param("queryEmbedding") String queryEmbedding,
            @Param("topK") int topK
    );
}