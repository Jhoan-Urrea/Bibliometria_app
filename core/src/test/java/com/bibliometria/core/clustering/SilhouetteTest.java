package com.bibliometria.core.clustering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class SilhouetteTest {

    @Test
    void calculatesTheMeanSilhouetteForTwoGroupsOnALine() {
        DistanceMatrix matrix = fromLineCoordinates(new double[] {0.0, 1.0, 2.0, 10.0, 11.0});
        int[] labels = {0, 0, 0, 1, 1};

        double[] scores = Silhouette.perDocumentScores(matrix, labels);

        assertThat(scores).hasSize(5);
        assertThat(scores[0]).isCloseTo(6.0 / 7.0, within(1e-12));
        assertThat(scores[1]).isCloseTo(17.0 / 19.0, within(1e-12));
        assertThat(scores[2]).isCloseTo(14.0 / 17.0, within(1e-12));
        assertThat(scores[3]).isCloseTo(8.0 / 9.0, within(1e-12));
        assertThat(scores[4]).isCloseTo(0.9, within(1e-12));
        assertThat(Silhouette.score(matrix, labels)).isCloseTo(0.87285959998, within(1e-10));
    }

    @Test
    void assignsZeroToSingletonClustersAndToSimultaneousZeroCohesionAndSeparation() {
        DistanceMatrix line = fromLineCoordinates(new double[] {0.0, 1.0, 4.0});
        assertThat(Silhouette.perDocumentScores(line, new int[] {0, 1, 1})[0]).isEqualTo(0.0);

        DistanceMatrix allZero =
                DistanceMatrix.fromDistances(
                        List.of("A", "B", "C", "D"),
                        new double[][] {
                            {0.0, 0.0, 0.0, 0.0},
                            {0.0, 0.0, 0.0, 0.0},
                            {0.0, 0.0, 0.0, 0.0},
                            {0.0, 0.0, 0.0, 0.0}
                        });
        assertThat(Silhouette.perDocumentScores(allZero, new int[] {0, 0, 1, 1}))
                .containsExactly(0.0, 0.0, 0.0, 0.0);
        assertThat(Silhouette.score(allZero, new int[] {0, 0, 1, 1})).isEqualTo(0.0);
    }

    @Test
    void validatesLabelsAndTheSupportedClusterCount() {
        DistanceMatrix matrix = fromLineCoordinates(new double[] {0.0, 1.0, 2.0});

        assertThatThrownBy(() -> Silhouette.score(matrix, new int[] {0, 0}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one cluster label");
        assertThatThrownBy(() -> Silhouette.score(matrix, new int[] {0, 0, 0}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 2 and 10");
        assertThatThrownBy(() -> Silhouette.score(matrix, new int[] {-1, 0, 1}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
        assertThatThrownBy(
                        () ->
                                Silhouette.score(
                                        fromLineCoordinates(
                                                new double[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10}),
                                        new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 2 and 10");
    }

    private static DistanceMatrix fromLineCoordinates(double[] coordinates) {
        int size = coordinates.length;
        List<String> ids =
                java.util.stream.IntStream.range(0, size)
                        .mapToObj(index -> "P%02d".formatted(index))
                        .toList();
        double[][] distances = new double[size][size];
        for (int first = 0; first < size; first++) {
            for (int second = first + 1; second < size; second++) {
                double distance = Math.abs(coordinates[first] - coordinates[second]) / 11.0;
                distances[first][second] = distance;
                distances[second][first] = distance;
            }
        }
        return DistanceMatrix.fromDistances(ids, distances);
    }
}
