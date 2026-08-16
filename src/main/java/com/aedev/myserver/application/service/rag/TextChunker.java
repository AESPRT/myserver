package com.aedev.myserver.application.service.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits raw document text into overlapping chunks before embedding.
 * <p>
 * Fixed-size character chunking with overlap, not sentence/paragraph-aware.
 * This is a deliberate simplicity trade-off for a domain-agnostic
 * service that has to handle arbitrary text (FAQs, articles, policy
 * docs, etc.) without per-domain tuning. Overlap exists so a fact that
 * happens to fall across a chunk boundary is still findable in at least
 * one whole chunk, at the cost of some duplicate content across chunks.
 */
@Component
public class TextChunker {

    private static final int DEFAULT_CHUNK_SIZE = 1000;
    private static final int DEFAULT_OVERLAP = 150;

    public List<String> chunk(String text) {
        return chunk(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public List<String> chunk(String text, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        if (overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap must be smaller than chunkSize");
        }

        String trimmed = text.strip();
        if (trimmed.length() <= chunkSize) {
            return List.of(trimmed);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        int step = chunkSize - overlap;

        while (start < trimmed.length()) {
            int end = Math.min(start + chunkSize, trimmed.length());
            chunks.add(trimmed.substring(start, end).strip());

            if (end == trimmed.length()) {
                break;
            }
            start += step;
        }

        return chunks;
    }
}