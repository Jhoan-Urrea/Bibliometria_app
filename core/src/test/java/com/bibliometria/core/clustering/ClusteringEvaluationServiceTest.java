package com.bibliometria.core.clustering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClusteringEvaluationServiceTest {

    @Test
    void evaluatesThreeLinkagesAndSelectsBestSilhouetteForEach() {
        DistanceMatrix matrix = fixtureMatrix();

        ClusteringEvaluationService.Evaluation evaluation =
                ClusteringEvaluationService.evaluate(matrix);

        assertThat(evaluation.linkages())
                .extracting(ClusteringEvaluationService.LinkageEvaluation::linkage)
                .containsExactly("single", "complete", "average");
        for (ClusteringEvaluationService.LinkageEvaluation candidate : evaluation.linkages()) {
            assertThat(candidate.silhouetteByK().keySet()).containsExactlyInAnyOrder(2, 3, 4);
            assertThat(candidate.bestK()).isBetween(2, 4);
            assertThat(candidate.bestSilhouette())
                    .isEqualTo(candidate.silhouetteByK().get(candidate.bestK()));
            assertThat(candidate.merges()).hasSize(3);
            assertThat(candidate.dendrogram().leafOrder())
                    .containsExactlyElementsOf(matrix.documentIds());
        }
        assertThat(evaluation.winner()).isIn(evaluation.linkages());
    }

    @Test
    void resolvesSilhouetteTiesWithinToleranceByHigherCpcc() {
        ClusteringEvaluationService.Evaluation baseline =
                ClusteringEvaluationService.evaluate(fixtureMatrix());
        ClusteringEvaluationService.LinkageEvaluation template = baseline.linkages().getFirst();
        List<ClusteringEvaluationService.LinkageEvaluation> candidates =
                List.of(
                        candidate(template, "single", 0.8, 0.6),
                        candidate(template, "complete", 0.8 + 0.5e-9, 0.9),
                        candidate(template, "average", 0.8 - 2.0e-9, 1.0));

        ClusteringEvaluationService.LinkageEvaluation winner =
                ClusteringEvaluationService.selectWinner(candidates);

        assertThat(winner.linkage()).isEqualTo("complete");
    }

    @Test
    void silhouetteWinsWhenItsAdvantageExceedsDecisionTolerance() {
        ClusteringEvaluationService.Evaluation baseline =
                ClusteringEvaluationService.evaluate(fixtureMatrix());
        ClusteringEvaluationService.LinkageEvaluation template = baseline.linkages().getFirst();
        List<ClusteringEvaluationService.LinkageEvaluation> candidates =
                List.of(
                        candidate(template, "single", 0.8, 0.9),
                        candidate(template, "complete", 0.8 + 2.0e-9, 0.1));

        assertThat(ClusteringEvaluationService.selectWinner(candidates).linkage())
                .isEqualTo("complete");
    }

    @Test
    void breaksRemainingExactTiesByStableLinkageOrder() {
        ClusteringEvaluationService.Evaluation baseline =
                ClusteringEvaluationService.evaluate(fixtureMatrix());
        ClusteringEvaluationService.LinkageEvaluation template = baseline.linkages().getFirst();
        List<ClusteringEvaluationService.LinkageEvaluation> candidates =
                List.of(
                        candidate(template, "single", 0.7, 0.5),
                        candidate(template, "complete", 0.7, 0.5),
                        candidate(template, "average", 0.7, 0.5));

        assertThat(ClusteringEvaluationService.selectWinner(candidates).linkage())
                .isEqualTo("single");
    }

    @Test
    void choosesSmallerKWhenSilhouetteScoresForKAreWithinTolerance() {
        DistanceMatrix matrix =
                DistanceMatrix.fromDistances(
                        List.of("A", "B", "C", "D"),
                        new double[][] {
                            {0.0, 0.0, 0.0, 0.0},
                            {0.0, 0.0, 0.0, 0.0},
                            {0.0, 0.0, 0.0, 0.0},
                            {0.0, 0.0, 0.0, 0.0}
                        });

        ClusteringEvaluationService.Evaluation evaluation =
                ClusteringEvaluationService.evaluate(matrix);

        assertThat(evaluation.linkages())
                .allSatisfy(candidate -> assertThat(candidate.bestK()).isEqualTo(2));
        assertThat(evaluation.winner().linkage()).isEqualTo("single");
    }

    @Test
    void rejectsInputsThatCannotBeEvaluated() {
        assertThatThrownBy(() -> ClusteringEvaluationService.evaluate(null))
                .isInstanceOf(NullPointerException.class);
        DistanceMatrix singleton =
                DistanceMatrix.fromDistances(List.of("A"), new double[][] {{0.0}});
        assertThatThrownBy(() -> ClusteringEvaluationService.evaluate(singleton))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClusteringEvaluationService.selectWinner(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ClusteringEvaluationService.LinkageEvaluation candidate(
            ClusteringEvaluationService.LinkageEvaluation template,
            String name,
            double silhouette,
            double cpcc) {
        return new ClusteringEvaluationService.LinkageEvaluation(
                name,
                cpcc,
                template.bestK(),
                silhouette,
                Map.of(template.bestK(), silhouette),
                template.merges(),
                template.dendrogram());
    }

    private static DistanceMatrix fixtureMatrix() {
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
