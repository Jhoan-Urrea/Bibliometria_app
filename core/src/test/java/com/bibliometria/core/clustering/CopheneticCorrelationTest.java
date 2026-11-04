package com.bibliometria.core.clustering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class CopheneticCorrelationTest {

    @Test
    void computesSingleLinkCpccForFourLeafFixture() {
        DistanceMatrix matrix =
                DistanceMatrix.fromDistances(
                        List.of("A", "B", "C", "D"),
                        new double[][] {
                            {0.0, 11.0 / 20, 5.0 / 20, 12.0 / 20},
                            {11.0 / 20, 0.0, 7.0 / 20, 15.0 / 20},
                            {5.0 / 20, 7.0 / 20, 0.0, 14.0 / 20},
                            {12.0 / 20, 15.0 / 20, 14.0 / 20, 0.0}
                        });
        Dendrogram dendrogram =
                Dendrogram.from(matrix, AgglomerativeClustering.run(matrix, new SingleLinkage()));

        assertThat(CopheneticCorrelation.compute(matrix, dendrogram))
                .isCloseTo(0.8985, within(0.0001));
    }

    @Test
    void returnsZeroWhenEitherPairwiseSeriesHasNoVariance() {
        DistanceMatrix equalDistances =
                DistanceMatrix.fromDistances(
                        List.of("A", "B", "C"),
                        new double[][] {{0.0, 0.5, 0.5}, {0.5, 0.0, 0.5}, {0.5, 0.5, 0.0}});
        Dendrogram dendrogram =
                Dendrogram.from(
                        equalDistances,
                        AgglomerativeClustering.run(equalDistances, new SingleLinkage()));

        assertThat(CopheneticCorrelation.compute(equalDistances, dendrogram)).isEqualTo(0.0);
    }

    @Test
    void rejectsDendrogramsWithDifferentCanonicalLeaves() {
        DistanceMatrix first =
                DistanceMatrix.fromDistances(
                        List.of("A", "B"), new double[][] {{0.0, 0.5}, {0.5, 0.0}});
        DistanceMatrix second =
                DistanceMatrix.fromDistances(
                        List.of("C", "D"), new double[][] {{0.0, 0.5}, {0.5, 0.0}});
        Dendrogram dendrogram =
                Dendrogram.from(second, AgglomerativeClustering.run(second, new SingleLinkage()));

        assertThatThrownBy(() -> CopheneticCorrelation.compute(first, dendrogram))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same canonical leaves");
    }
}
