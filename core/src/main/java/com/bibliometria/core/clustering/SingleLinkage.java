package com.bibliometria.core.clustering;

/**
 * Single-link criterion: {@code d(A∪B,C)=min(d(A,C),d(B,C))}.
 *
 * <p>Its Lance-Williams coefficients are {@code alphaA=alphaB=1/2}, {@code beta=0}, and {@code
 * gamma=-1/2}.
 */
public final class SingleLinkage implements Linkage {

    /** Creates the single-link criterion. */
    public SingleLinkage() {}

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

    /** Returns {@code beta=0}; single-link does not use {@code d(A,B)} in the update. */
    @Override
    public double beta() {
        return 0.0;
    }

    /** Returns {@code gamma=-1/2}, yielding the minimum-distance update. */
    @Override
    public double gamma() {
        return -0.5;
    }
}
