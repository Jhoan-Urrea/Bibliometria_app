package com.bibliometria.core.clustering;

/** Single-link (minimum) Lance-Williams criterion. */
public final class SingleLinkage implements Linkage {

    @Override
    public double alphaA(int sizeA, int sizeB) {
        return 0.5;
    }

    @Override
    public double alphaB(int sizeA, int sizeB) {
        return 0.5;
    }

    @Override
    public double beta() {
        return 0.0;
    }

    @Override
    public double gamma() {
        return -0.5;
    }
}
