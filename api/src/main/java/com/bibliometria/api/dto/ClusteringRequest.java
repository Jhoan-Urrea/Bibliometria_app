package com.bibliometria.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ClusteringRequest(@Min(1) @Max(20) int clusterCount, @NotNull LinkageDto linkage) {}
