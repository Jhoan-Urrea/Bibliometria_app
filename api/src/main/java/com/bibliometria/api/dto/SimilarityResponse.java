package com.bibliometria.api.dto;

public record SimilarityResponse(
        String articleAId,
        String articleBId,
        SimilarityAlgorithmDto algorithm,
        double rawScore,
        double normalizedScore) {}
