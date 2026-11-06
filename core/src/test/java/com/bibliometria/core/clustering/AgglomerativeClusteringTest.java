package com.bibliometria.core.clustering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class AgglomerativeClusteringTest {

    @Test
    void matchesTheFourElementLanceWilliamsFixture() {
        DistanceMatrix matrix =
                DistanceMatrix.fromDistances(
                        List.of("A", "B", "C", "D"),
                        new double[][] {
                            {0.0, 0.1, 0.4, 0.7},
                            {0.1, 0.0, 0.5, 0.8},
                            {0.4, 0.5, 0.0, 0.3},
                            {0.7, 0.8, 0.3, 0.0}
                        });

        assertMerges(
                AgglomerativeClustering.run(matrix, new SingleLinkage()),
                List.of(new Merge(0, 1, 0.1, 2), new Merge(2, 3, 0.3, 2), new Merge(4, 5, 0.4, 4)));
        assertMerges(
                AgglomerativeClustering.run(matrix, new CompleteLinkage()),
                List.of(new Merge(0, 1, 0.1, 2), new Merge(2, 3, 0.3, 2), new Merge(4, 5, 0.8, 4)));
        assertMerges(
                AgglomerativeClustering.run(matrix, new AverageLinkage()),
                List.of(new Merge(0, 1, 0.1, 2), new Merge(2, 3, 0.3, 2), new Merge(4, 5, 0.6, 4)));
    }

    @Test
    void resolvesEqualDistancesByTheSmallestActiveClusterIndices() {
        DistanceMatrix matrix =
                DistanceMatrix.fromDistances(
                        List.of("A", "B", "C", "D"),
                        new double[][] {
                            {0.0, 0.0, 0.0, 0.0},
                            {0.0, 0.0, 0.0, 0.0},
                            {0.0, 0.0, 0.0, 0.0},
                            {0.0, 0.0, 0.0, 0.0}
                        });

        assertThat(AgglomerativeClustering.run(matrix, new SingleLinkage()))
                .containsExactly(
                        new Merge(0, 1, 0.0, 2), new Merge(2, 3, 0.0, 2), new Merge(4, 5, 0.0, 4));
    }

    @Test
    void singleAndCompleteLinkageMatchTheirMinimumAndMaximumDefinitionsForRandomDistances() {
        Random random = new Random(6_102_2026L);
        Linkage single = new SingleLinkage();
        Linkage complete = new CompleteLinkage();
        for (int iteration = 0; iteration < 1_000; iteration++) {
            double distanceAC = random.nextDouble();
            double distanceBC = random.nextDouble();
            double distanceAB = random.nextDouble();

            assertThat(single.update(distanceAC, distanceBC, distanceAB, 2, 3))
                    .isCloseTo(Math.min(distanceAC, distanceBC), within(1e-12));
            assertThat(complete.update(distanceAC, distanceBC, distanceAB, 2, 3))
                    .isCloseTo(Math.max(distanceAC, distanceBC), within(1e-12));
        }
    }

    @Test
    void randomSingleAndCompleteHierarchiesAreDeterministicAndMonotonic() {
        Random random = new Random(42);
        for (int iteration = 0; iteration < 30; iteration++) {
            DistanceMatrix matrix = randomMatrix(random, 4 + random.nextInt(7));
            for (Linkage linkage : List.of(new SingleLinkage(), new CompleteLinkage())) {
                List<Merge> first = AgglomerativeClustering.run(matrix, linkage);
                List<Merge> second = AgglomerativeClustering.run(matrix, linkage);

                assertThat(first).isEqualTo(second).hasSize(matrix.size() - 1);
                double previousHeight = 0.0;
                for (Merge merge : first) {
                    assertThat(merge.height()).isBetween(previousHeight, 1.0);
                    assertThat(merge.size()).isGreaterThanOrEqualTo(2);
                    assertThat(merge.leftChild()).isLessThan(merge.rightChild());
                    previousHeight = merge.height();
                }
            }
        }
    }

    @Test
    void averageLinkageUsesClusterSizesAndAllLinkagesRejectInvalidInput() {
        AverageLinkage average = new AverageLinkage();

        assertThat(average.update(0.2, 0.8, 0.5, 1, 3)).isCloseTo(0.65, within(1e-12));
        assertThat(average.update(0.2, 0.8, 0.5, 3, 1)).isCloseTo(0.35, within(1e-12));
        assertThatThrownBy(() -> new AverageLinkage().update(0.2, 0.8, 0.5, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SingleLinkage().update(Double.NaN, 0.8, 0.5, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgglomerativeClustering.run(null, new SingleLinkage()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AgglomerativeClustering.run(randomMatrix(new Random(1), 4), null))
                .isInstanceOf(NullPointerException.class);
    }

    private static DistanceMatrix randomMatrix(Random random, int size) {
        List<String> ids = new ArrayList<>(size);
        double[][] distances = new double[size][size];
        for (int index = 0; index < size; index++) {
            ids.add("D%02d".formatted(index));
            for (int other = index + 1; other < size; other++) {
                distances[index][other] = random.nextDouble();
                distances[other][index] = distances[index][other];
            }
        }
        return DistanceMatrix.fromDistances(ids, distances);
    }

    private static void assertMerges(List<Merge> actual, List<Merge> expected) {
        assertThat(actual).hasSize(expected.size());
        for (int index = 0; index < expected.size(); index++) {
            Merge actualMerge = actual.get(index);
            Merge expectedMerge = expected.get(index);
            assertThat(actualMerge.leftChild()).isEqualTo(expectedMerge.leftChild());
            assertThat(actualMerge.rightChild()).isEqualTo(expectedMerge.rightChild());
            assertThat(actualMerge.height()).isCloseTo(expectedMerge.height(), within(1e-12));
            assertThat(actualMerge.size()).isEqualTo(expectedMerge.size());
        }
    }
}
