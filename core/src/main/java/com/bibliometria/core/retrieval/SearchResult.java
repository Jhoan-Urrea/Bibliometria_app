package com.bibliometria.core.retrieval;

import java.util.List;
import java.util.Objects;

/** Immutable page of deterministically ranked search hits. */
public record SearchResult(
        String query,
        List<ScoredDocument> results,
        int totalHits,
        int page,
        int pageSize,
        int totalPages) {

    public SearchResult {
        Objects.requireNonNull(query, "query");
        results = List.copyOf(Objects.requireNonNull(results, "results"));
        if (totalHits < 0 || page < 1 || pageSize < 1 || totalPages < 0) {
            throw new IllegalArgumentException("Invalid search result pagination metadata.");
        }
        if (results.size() > pageSize || results.size() > totalHits) {
            throw new IllegalArgumentException("Search page exceeds its declared result bounds.");
        }
    }
}
