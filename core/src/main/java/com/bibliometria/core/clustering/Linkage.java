package com.bibliometria.core.clustering;

/**
 * Lance-Williams criterion for updating distances after merging two clusters.
 *
 * <p>For clusters {@code A}, {@code B}, and {@code C}, the update is {@code d(A∪B,C) = αA*d(A,C) +
 * αB*d(B,C) + β*d(A,B) + γ*|d(A,C)-d(B,C)|}. Implementations provide the coefficients for {@code A}
 * and {@code B}; average linkage may depend on their cluster sizes.
 */
public interface Linkage {

    double alphaA(int sizeA, int sizeB);

    double alphaB(int sizeA, int sizeB);

    double beta();

    double gamma();

    /**
     * Applies the Lance-Williams recurrence.
     *
     * @param distanceAC distance from cluster A to cluster C
     * @param distanceBC distance from cluster B to cluster C
     * @param distanceAB distance between the clusters being merged
     * @param sizeA number of original documents in cluster A
     * @param sizeB number of original documents in cluster B
     * @return updated distance from A∪B to C, clamped for floating-point noise
     */
    default double update(
            double distanceAC, double distanceBC, double distanceAB, int sizeA, int sizeB) {
        if (sizeA < 1 || sizeB < 1) {
            throw new IllegalArgumentException("Cluster sizes must be positive.");
        }
        requireDistance(distanceAC, "distanceAC");
        requireDistance(distanceBC, "distanceBC");
        requireDistance(distanceAB, "distanceAB");

        double result =
                alphaA(sizeA, sizeB) * distanceAC
                        + alphaB(sizeA, sizeB) * distanceBC
                        + beta() * distanceAB
                        + gamma() * Math.abs(distanceAC - distanceBC);
        if (!Double.isFinite(result) || result < -1e-12 || result > 1.0 + 1e-12) {
            throw new IllegalArgumentException(
                    "Updated linkage distance must be finite and in [0, 1].");
        }
        return Math.max(0.0, Math.min(1.0, result));
    }

    private static void requireDistance(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1].");
        }
    }
}
