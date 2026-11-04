package com.bibliometria.core.clustering;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable matrix of cophenetic distances induced by a dendrogram.
 *
 * <p>For leaves {@code x} and {@code y}, {@code h(x,y)} is the height of their lowest common
 * ancestor, i.e. the fusion at which the two leaves first belong to the same cluster.
 */
public final class CopheneticMatrix {

    private final List<String> documentIds;
    private final Map<String, Integer> indexById;
    private final double[][] heights;

    CopheneticMatrix(List<String> documentIds, double[][] heights) {
        this.documentIds = List.copyOf(documentIds);
        Map<String, Integer> indices = new HashMap<>();
        for (int index = 0; index < documentIds.size(); index++) {
            indices.put(documentIds.get(index), index);
        }
        this.indexById = Map.copyOf(indices);
        this.heights = new double[heights.length][];
        for (int row = 0; row < heights.length; row++) {
            this.heights[row] = heights[row].clone();
        }
        validar();
    }

    /** Returns the number of leaves represented by this matrix. */
    public int size() {
        return documentIds.size();
    }

    /** Returns immutable document IDs in canonical order. */
    public List<String> documentIds() {
        return documentIds;
    }

    /**
     * Returns the cophenetic height for the pair of leaves at the specified indices.
     *
     * @throws IndexOutOfBoundsException if either index is outside the matrix
     */
    public double height(int firstIndex, int secondIndex) {
        return heights[firstIndex][secondIndex];
    }

    /**
     * Returns the cophenetic height for the pair of document IDs.
     *
     * @throws NullPointerException if either ID is null
     * @throws IllegalArgumentException if either ID is not in this matrix
     */
    public double height(String firstId, String secondId) {
        Integer first = indexById.get(Objects.requireNonNull(firstId, "firstId"));
        Integer second = indexById.get(Objects.requireNonNull(secondId, "secondId"));
        if (first == null || second == null) {
            throw new IllegalArgumentException("Both document IDs must exist in the matrix.");
        }
        return heights[first][second];
    }

    /** Returns a defensive copy of the complete matrix. */
    public double[][] toArray() {
        double[][] copy = new double[heights.length][];
        for (int row = 0; row < heights.length; row++) {
            copy[row] = heights[row].clone();
        }
        return copy;
    }

    /** Verifies matrix dimensions, exact zero diagonal, symmetry, and finite heights in [0, 1]. */
    public void validar() {
        if (heights.length != documentIds.size()) {
            throw new IllegalStateException("Cophenetic matrix must match its document IDs.");
        }
        for (int row = 0; row < heights.length; row++) {
            if (heights[row].length != heights.length) {
                throw new IllegalStateException("Cophenetic matrix must be square.");
            }
            if (heights[row][row] != 0.0) {
                throw new IllegalStateException("Cophenetic matrix diagonal must be exactly zero.");
            }
            for (int column = 0; column < heights.length; column++) {
                double height = heights[row][column];
                if (!Double.isFinite(height) || height < 0.0 || height > 1.0) {
                    throw new IllegalStateException(
                            "Cophenetic heights must be finite and in [0, 1].");
                }
                if (height != heights[column][row]) {
                    throw new IllegalStateException("Cophenetic matrix must be symmetric.");
                }
            }
        }
    }
}
