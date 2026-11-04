package com.bibliometria.core.clustering;

/** UPGMA linkage, weighted by the number of original documents in each merged cluster. */
public final class AverageLinkage implements Linkage {

    @Override
    public double alphaA(int sizeA, int sizeB) {
        requirePositiveSizes(sizeA, sizeB);
        return (double) sizeA / (sizeA + sizeB);
    }

    @Override
    public double alphaB(int sizeA, int sizeB) {
        requirePositiveSizes(sizeA, sizeB);
        return (double) sizeB / (sizeA + sizeB);
    }

    @Override
    public double beta() {
        return 0.0;
    }

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
