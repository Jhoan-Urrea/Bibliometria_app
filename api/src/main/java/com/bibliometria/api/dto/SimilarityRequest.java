package com.bibliometria.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SimilarityRequest(
        @NotBlank @Size(max = 32) String articleAId,
        @NotBlank @Size(max = 32) String articleBId,
        @NotNull SimilarityAlgorithmDto algorithm) {}
