package com.bibliometria.core.clustering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class DendrogramTest {

    @Test
    void buildsCanonicalLeafOrderAndCopheneticHeightsFromFourLeafFixture() {
        DistanceMatrix matrix = fourDocumentMatrix();
        List<Merge> merges = AgglomerativeClustering.run(matrix, new SingleLinkage());
        Dendrogram dendrogram = Dendrogram.from(matrix, merges);
        CopheneticMatrix cophenetic = dendrogram.copheneticMatrix();

        assertThat(dendrogram.leafOrder()).containsExactly("A", "B", "C", "D");
        assertThat(dendrogram.root().size()).isEqualTo(4);
        assertThat(dendrogram.root().height()).isCloseTo(0.4, within(1e-12));
        assertThat(cophenetic.documentIds()).containsExactly("A", "B", "C", "D");
        assertThat(cophenetic.height("A", "B")).isCloseTo(0.1, within(1e-12));
        assertThat(cophenetic.height("C", "D")).isCloseTo(0.3, within(1e-12));
        assertThat(cophenetic.height("A", "C")).isCloseTo(0.4, within(1e-12));
        assertThat(cophenetic.height("A", "D")).isCloseTo(0.4, within(1e-12));
        assertThat(cophenetic.height("B", "C")).isCloseTo(0.4, within(1e-12));
        assertThat(cophenetic.height("B", "D")).isCloseTo(0.4, within(1e-12));
        assertThat(cophenetic.height("A", "A")).isEqualTo(0.0);
    }

    @Test
    void copheneticMatrixIsSymmetricAndUltrametricForEachLinkage() {
        DistanceMatrix matrix = fourDocumentMatrix();
        for (Linkage linkage :
                List.of(new SingleLinkage(), new CompleteLinkage(), new AverageLinkage())) {
            CopheneticMatrix cophenetic =
                    Dendrogram.from(matrix, AgglomerativeClustering.run(matrix, linkage))
                            .copheneticMatrix();
            double[][] values = cophenetic.toArray();

            for (int first = 0; first < cophenetic.size(); first++) {
                assertThat(values[first][first]).isEqualTo(0.0);
                for (int second = 0; second < cophenetic.size(); second++) {
                    assertThat(values[first][second]).isBetween(0.0, 1.0);
                    assertThat(values[first][second]).isEqualTo(values[second][first]);
                    for (int third = 0; third < cophenetic.size(); third++) {
                        assertThat(values[first][second])
                                .isLessThanOrEqualTo(
                                        Math.max(values[first][third], values[third][second])
                                                + 1e-12);
                    }
                }
            }
        }
    }

    @Test
    void exposesAnImmutableTreeAndDefensiveCopheneticMatrix() {
        DistanceMatrix matrix = fourDocumentMatrix();
        Dendrogram dendrogram =
                Dendrogram.from(matrix, AgglomerativeClustering.run(matrix, new SingleLinkage()));
        CopheneticMatrix cophenetic = dendrogram.copheneticMatrix();
        double[][] copy = cophenetic.toArray();
        copy[0][1] = 0.9;

        assertThat(cophenetic.height("A", "B")).isCloseTo(0.1, within(1e-12));
        assertThatThrownBy(() -> dendrogram.leafOrder().add("X"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> dendrogram.root().documentId())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cophenetic.height("unknown", "A"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void supportsSingleLeafTreeAndRejectsIncompleteOrNonUltrametricMergeHistories() {
        DistanceMatrix singleton =
                DistanceMatrix.fromDistances(List.of("A"), new double[][] {{0.0}});
        Dendrogram oneLeaf = Dendrogram.from(singleton, List.of());

        assertThat(oneLeaf.leafOrder()).containsExactly("A");
        assertThat(oneLeaf.root().isLeaf()).isTrue();
        assertThat(oneLeaf.copheneticMatrix().height("A", "A")).isEqualTo(0.0);

        DistanceMatrix matrix = fourDocumentMatrix();
        assertThatThrownBy(() -> Dendrogram.from(matrix, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                Dendrogram.from(
                                        matrix,
                                        List.of(
                                                new Merge(0, 1, 0.5, 2),
                                                new Merge(2, 3, 0.3, 2),
                                                new Merge(4, 5, 0.4, 4))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nondecreasing");
        assertThatThrownBy(
                        () ->
                                Dendrogram.from(
                                        matrix,
                                        List.of(
                                                new Merge(0, 1, 0.1, 2),
                                                new Merge(2, 3, 0.3, 2),
                                                new Merge(0, 4, 0.4, 4))))
                .isInstanceOf(IllegalArgumentException.class);
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
