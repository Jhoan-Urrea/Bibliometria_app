package com.bibliometria.core.clustering;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Evaluates single, complete, and average-link hierarchies using CPCC and mean silhouette.
 *
 * <p>For each linkage, the best cut maximizes {@code S(K)} for {@code K=2..min(10,N)}. If
 * silhouette values for K differ by no more than {@value #DECISION_TOLERANCE}, the smaller K is
 * retained as the less fragmented solution. Across linkages, the candidate with the higher best
 * silhouette wins; values within the same tolerance are resolved by the higher CPCC. A remaining
 * exact tie follows the deterministic order single, complete, average.
 */
public final class ClusteringEvaluationService {

    public static final double DECISION_TOLERANCE = 1e-9;

    private ClusteringEvaluationService() {}

    /**
     * Evaluates all supported linkages and returns the selected configuration and their full
     * metrics.
     *
     * @param matrix validated pairwise distances, with at least two documents
     * @return immutable evaluation ordered single, complete, average
     * @throws NullPointerException if {@code matrix} is null
     * @throws IllegalArgumentException if the matrix has fewer than two documents
     */
    public static Evaluation evaluate(DistanceMatrix matrix) {
        Objects.requireNonNull(matrix, "matrix");
        matrix.validar();
        if (matrix.size() < 2) {
            throw new IllegalArgumentException(
                    "Clustering evaluation requires at least two leaves.");
        }

        List<Linkage> linkages =
                List.of(new SingleLinkage(), new CompleteLinkage(), new AverageLinkage());
        List<LinkageEvaluation> evaluations = new ArrayList<>(linkages.size());
        for (Linkage linkage : linkages) {
            List<Merge> merges = AgglomerativeClustering.run(matrix, linkage);
            Dendrogram dendrogram = Dendrogram.from(matrix, merges);
            double cpcc = CopheneticCorrelation.compute(matrix, dendrogram);

            int bestK = 2;
            double bestSilhouette = Double.NEGATIVE_INFINITY;
            Map<Integer, Double> silhouetteByK = new LinkedHashMap<>();
            int maximumK = Math.min(Silhouette.MAX_CLUSTERS, matrix.size());
            for (int k = Silhouette.MIN_CLUSTERS; k <= maximumK; k++) {
                int[] labels = ClusterCut.cut(merges, k, matrix.size());
                double score = Silhouette.score(matrix, labels);
                silhouetteByK.put(k, score);
                if (score > bestSilhouette + DECISION_TOLERANCE
                        || (Math.abs(score - bestSilhouette) <= DECISION_TOLERANCE && k < bestK)) {
                    bestK = k;
                    bestSilhouette = score;
                }
            }
            evaluations.add(
                    new LinkageEvaluation(
                            linkageName(linkage),
                            cpcc,
                            bestK,
                            bestSilhouette,
                            silhouetteByK,
                            merges,
                            dendrogram));
        }

        LinkageEvaluation winner = selectWinner(evaluations);
        return new Evaluation(evaluations, winner);
    }

    static LinkageEvaluation selectWinner(List<LinkageEvaluation> evaluations) {
        Objects.requireNonNull(evaluations, "evaluations");
        if (evaluations.isEmpty()) {
            throw new IllegalArgumentException("At least one linkage evaluation is required.");
        }
        LinkageEvaluation winner = evaluations.getFirst();
        for (int index = 1; index < evaluations.size(); index++) {
            LinkageEvaluation candidate =
                    Objects.requireNonNull(evaluations.get(index), "evaluation");
            if (candidate.bestSilhouette() > winner.bestSilhouette() + DECISION_TOLERANCE
                    || (Math.abs(candidate.bestSilhouette() - winner.bestSilhouette())
                                    <= DECISION_TOLERANCE
                            && candidate.cpcc() > winner.cpcc())) {
                winner = candidate;
            }
        }
        return winner;
    }

    private static String linkageName(Linkage linkage) {
        if (linkage instanceof SingleLinkage) {
            return "single";
        }
        if (linkage instanceof CompleteLinkage) {
            return "complete";
        }
        if (linkage instanceof AverageLinkage) {
            return "average";
        }
        throw new IllegalArgumentException("Unsupported linkage implementation.");
    }

    /** Immutable metrics and hierarchy for one linkage criterion. */
    public record LinkageEvaluation(
            String linkage,
            double cpcc,
            int bestK,
            double bestSilhouette,
            Map<Integer, Double> silhouetteByK,
            List<Merge> merges,
            Dendrogram dendrogram) {

        public LinkageEvaluation {
            Objects.requireNonNull(linkage, "linkage");
            Objects.requireNonNull(silhouetteByK, "silhouetteByK");
            Objects.requireNonNull(merges, "merges");
            Objects.requireNonNull(dendrogram, "dendrogram");
            if (linkage.isBlank()
                    || !Double.isFinite(cpcc)
                    || cpcc < -1.0
                    || cpcc > 1.0
                    || bestK < 2
                    || !Double.isFinite(bestSilhouette)
                    || bestSilhouette < -1.0
                    || bestSilhouette > 1.0) {
                throw new IllegalArgumentException("Invalid clustering evaluation metrics.");
            }
            silhouetteByK = Map.copyOf(silhouetteByK);
            merges = List.copyOf(merges);
        }
    }

    /** Immutable evaluation of all three linkage criteria and the selected winner. */
    public record Evaluation(List<LinkageEvaluation> linkages, LinkageEvaluation winner) {

        public Evaluation {
            linkages = List.copyOf(Objects.requireNonNull(linkages, "linkages"));
            Objects.requireNonNull(winner, "winner");
            if (!linkages.contains(winner) || linkages.size() != 3) {
                throw new IllegalArgumentException(
                        "Evaluation must contain its three linkages and winner.");
            }
        }
    }
}
