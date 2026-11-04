package com.bibliometria.core.clustering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ClusterCutTest {

    @Test
    void cutsSingleLinkHierarchyIntoCanonicalLabels() {
        List<Merge> merges = AgglomerativeClustering.run(fourDocumentMatrix(), new SingleLinkage());

        assertThat(ClusterCut.cut(merges, 4, 4)).containsExactly(0, 1, 2, 3);
        assertThat(ClusterCut.cut(merges, 3, 4)).containsExactly(0, 0, 1, 2);
        assertThat(ClusterCut.cut(merges, 2, 4)).containsExactly(0, 0, 1, 1);
        assertThat(ClusterCut.cut(merges, 1, 4)).containsExactly(0, 0, 0, 0);
    }

    @Test
    void validatesCutDimensionsAndCompleteMergeHistory() {
        List<Merge> merges = AgglomerativeClustering.run(fourDocumentMatrix(), new SingleLinkage());

        assertThatThrownBy(() -> ClusterCut.cut(merges, 0, 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClusterCut.cut(merges, 5, 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClusterCut.cut(merges.subList(0, 2), 2, 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                ClusterCut.cut(
                                        List.of(
                                                new Merge(0, 1, 0.1, 2),
                                                new Merge(0, 2, 0.2, 3),
                                                new Merge(3, 4, 0.3, 4)),
                                        2,
                                        4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("active nodes");
    }

    private static DistanceMatrix fourDocumentMatrix() {
        return DistanceMatrix.fromDistances(
                List.of("A", "B", "C", "D"),
                new double[][] {
                    {0.0, 0.1, 0.4, 0.7},
                    {0.1, 0.0, 0.5, 0.8},
                    {0.4, 0.5, 0.0, 0.3},
                    {0.7, 0.8, 0.3, 0.0}
                });
    }
}
