package com.bibliometria.core.clustering;

/**
 * Immutable agglomeration step.
 *
 * @param leftChild smaller cluster node index; initial leaves use {@code 0..n-1}
 * @param rightChild larger cluster node index; newly created cluster IDs follow merge order
 * @param height distance at which the two children are merged
 * @param size number of original leaves represented by the new cluster
 */
public record Merge(int leftChild, int rightChild, double height, int size) {

    public Merge {
        if (leftChild < 0 || rightChild <= leftChild) {
            throw new IllegalArgumentException(
                    "Merge children must be non-negative and ascending.");
        }
        if (!Double.isFinite(height) || height < 0.0 || height > 1.0) {
            throw new IllegalArgumentException("Merge height must be finite and in [0, 1].");
        }
        if (size < 2) {
            throw new IllegalArgumentException(
                    "A merged cluster must contain at least two leaves.");
        }
    }
}
