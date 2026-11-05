package com.bibliometria.api.dto;

import java.util.List;

public record ClusteringResponse(
        List<String> articleIds,
        LinkageDto linkage,
        List<ClusterDto> clusters,
        List<MergeDto> merges) {

    public ClusteringResponse {
        articleIds = List.copyOf(articleIds);
        clusters = List.copyOf(clusters);
        merges = List.copyOf(merges);
    }

    public record ClusterDto(int label, List<String> articleIds) {
        public ClusterDto {
            articleIds = List.copyOf(articleIds);
        }
    }

    public record MergeDto(int leftChild, int rightChild, double height, int size) {}
}
