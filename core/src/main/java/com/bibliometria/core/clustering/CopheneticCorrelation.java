package com.bibliometria.core.clustering;

import java.util.List;
import java.util.Objects;

/** Computes Pearson's correlation between original and cophenetic pairwise distances. */
public final class CopheneticCorrelation {

    private CopheneticCorrelation() {}

    /**
     * Computes the cophenetic correlation coefficient (CPCC) in two passes over the {@code
     * M=N(N-1)/2} unordered document pairs.
     *
     * <p>The first pass computes the means of the original distances \(\delta_{ij}\) and cophenetic
     * heights \(h_{ij}\). The second pass computes the centered cross-product and sums of squares:
     * \[ r_c=\frac{\sum_{i<j}(\delta_{ij}-\bar\delta)(h_{ij}-\bar h)}
     * {\sqrt{\sum_{i<j}(\delta_{ij}-\bar\delta)^2 \sum_{i<j}(h_{ij}-\bar h)^2}}. \] The result is
     * {@code 0} when either input has zero variance.
     *
     * @param delta original validated pairwise distances
     * @param dendrogram hierarchy with the same leaves in the same canonical order
     * @return Pearson correlation in {@code [-1, 1]}, or zero if variance is zero
     * @throws NullPointerException if either argument is null
     * @throws IllegalArgumentException if leaf count or canonical IDs do not match
     */
    public static double compute(DistanceMatrix delta, Dendrogram dendrogram) {
        Objects.requireNonNull(delta, "delta");
        Objects.requireNonNull(dendrogram, "dendrogram");
        delta.validar();

        List<String> expectedIds = delta.documentIds();
        if (!expectedIds.equals(dendrogram.documentIds())) {
            throw new IllegalArgumentException(
                    "Distance matrix and dendrogram must contain the same canonical leaves.");
        }

        int size = delta.size();
        long pairCount = (long) size * (size - 1) / 2;
        if (pairCount == 0) {
            return 0.0;
        }

        CopheneticMatrix cophenetic = dendrogram.copheneticMatrix();
        double deltaSum = 0.0;
        double heightSum = 0.0;
        for (int first = 0; first < size; first++) {
            for (int second = first + 1; second < size; second++) {
                deltaSum += delta.distance(first, second);
                heightSum += cophenetic.height(first, second);
            }
        }

        double deltaMean = deltaSum / pairCount;
        double heightMean = heightSum / pairCount;
        double centeredProduct = 0.0;
        double deltaSquares = 0.0;
        double heightSquares = 0.0;
        for (int first = 0; first < size; first++) {
            for (int second = first + 1; second < size; second++) {
                double centeredDelta = delta.distance(first, second) - deltaMean;
                double centeredHeight = cophenetic.height(first, second) - heightMean;
                centeredProduct += centeredDelta * centeredHeight;
                deltaSquares += centeredDelta * centeredDelta;
                heightSquares += centeredHeight * centeredHeight;
            }
        }

        if (deltaSquares == 0.0 || heightSquares == 0.0) {
            return 0.0;
        }
        return centeredProduct / Math.sqrt(deltaSquares * heightSquares);
    }
}
