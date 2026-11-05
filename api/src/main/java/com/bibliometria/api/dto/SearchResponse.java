package com.bibliometria.api.dto;

import java.util.List;

public record SearchResponse(
        String query,
        List<SearchHitDto> results,
        int totalHits,
        int page,
        int pageSize,
        int totalPages) {

    public SearchResponse {
        results = List.copyOf(results);
    }

    public record SearchHitDto(
            ArticleDto article, double score, double lexicalScore, double semanticScore) {}
}
