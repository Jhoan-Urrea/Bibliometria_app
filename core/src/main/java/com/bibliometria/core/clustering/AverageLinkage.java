package com.bibliometria.core.clustering;

/**
 * UPGMA linkage, weighted by the number of original documents in each merged cluster.
 *
 * <p>{@code alphaA=|A|/(|A|+|B|)}, {@code alphaB=|B|/(|A|+|B|)}, and {@code beta=gamma=0};
 * therefore {@code d(A∪B,C)} is the size-weighted mean of {@code d(A,C)} and {@code d(B,C)}.
 */
public final class AverageLinkage implements Linkage {

    /** Creates the size-weighted UPGMA linkage criterion. */
    public AverageLinkage() {}

    /** Returns {@code |A|/(|A|+|B|)} after validating both cluster sizes. */
    @Override
    public double alphaA(int sizeA, int sizeB) {
        requirePositiveSizes(sizeA, sizeB);
        return (double) sizeA / (sizeA + sizeB);
    }

    /** Returns {@code |B|/(|A|+|B|)} after validating both cluster sizes. */
    @Override
    public double alphaB(int sizeA, int sizeB) {
        requirePositiveSizes(sizeA, sizeB);
        return (double) sizeB / (sizeA + sizeB);
    }

    /** Returns {@code beta=0}; UPGMA has no additive {@code d(A,B)} term. */
    @Override
    public double beta() {
        return 0.0;
    }

    /** Returns {@code gamma=0}; UPGMA has no absolute-difference term. */
    @Override
    public double gamma() {
        return 0.0;
    }

    private static void requirePositiveSizes(int sizeA, int sizeB) {
        if (sizeA < 1 || sizeB < 1) {
            throw new IllegalArgumentException("Cluster sizes must be positive.");
        }
    }
}
