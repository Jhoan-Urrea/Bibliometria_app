package com.bibliometria.core.clustering;

/**
 * Complete-link criterion: {@code d(A∪B,C)=max(d(A,C),d(B,C))}.
 *
 * <p>Its Lance-Williams coefficients are {@code alphaA=alphaB=1/2}, {@code beta=0}, and {@code
 * gamma=1/2}.
 */
public final class CompleteLinkage implements Linkage {

    /** Creates the complete-link criterion. */
    public CompleteLinkage() {}

    /** Returns {@code alphaA=1/2}, independent of cluster sizes. */
    @Override
    public double alphaA(int sizeA, int sizeB) {
        return 0.5;
    }

    /** Returns {@code alphaB=1/2}, independent of cluster sizes. */
    @Override
    public double alphaB(int sizeA, int sizeB) {
        return 0.5;
    }

    /** Returns {@code beta=0}; complete-link does not use {@code d(A,B)} in the update. */
    @Override
    public double beta() {
        return 0.0;
    }

    /** Returns {@code gamma=1/2}, yielding the maximum-distance update. */
    @Override
    public double gamma() {
        return 0.5;
    }
}
