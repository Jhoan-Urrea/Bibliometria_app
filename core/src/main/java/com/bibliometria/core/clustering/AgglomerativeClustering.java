package com.bibliometria.core.clustering;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Deterministic bottom-up hierarchical clustering over an immutable distance matrix. */
public final class AgglomerativeClustering {

    private static final double TOLERANCE = 1e-12;

    private AgglomerativeClustering() {}

    /**
     * Builds a complete hierarchy using the supplied linkage criterion.
     *
     * <p>At each step the active pair with minimum distance is selected. Exact distance ties are
     * resolved lexicographically by cluster node indices, always selecting the pair with the
     * smallest first index and then smallest second index. Leaves use the matrix's canonical
     * indices {@code 0..n-1}; each new cluster receives {@code n + mergeNumber}. Children in each
     * {@link Merge} are stored in ascending index order.
     *
     * @param matrix validated pairwise distances
     * @param linkage Lance-Williams distance update criterion
     * @return immutable sequence of exactly {@code n-1} merges
     */
    public static List<Merge> run(DistanceMatrix matrix, Linkage linkage) {
        Objects.requireNonNull(matrix, "matrix");
        Objects.requireNonNull(linkage, "linkage");
        matrix.validar();

        int leafCount = matrix.size();
        if (leafCount < 2) {
            return List.of();
        }
        int nodeCapacity = Math.subtractExact(Math.multiplyExact(leafCount, 2), 1);
        double[][] distances = new double[nodeCapacity][nodeCapacity];
        double[][] initial = matrix.toArray();
        for (int row = 0; row < leafCount; row++) {
            System.arraycopy(initial[row], 0, distances[row], 0, leafCount);
        }

        int[] clusterSizes = new int[nodeCapacity];
        boolean[] active = new boolean[nodeCapacity];
        for (int leaf = 0; leaf < leafCount; leaf++) {
            clusterSizes[leaf] = 1;
            active[leaf] = true;
        }

        List<Merge> merges = new ArrayList<>(leafCount - 1);
        int nextNode = leafCount;
        for (int mergeIndex = 0; mergeIndex < leafCount - 1; mergeIndex++) {
            Pair closest = findClosestPair(active, distances);
            int left = closest.left();
            int right = closest.right();
            int mergedSize = Math.addExact(clusterSizes[left], clusterSizes[right]);

            for (int other = 0; other < nextNode; other++) {
                if (active[other] && other != left && other != right) {
                    double updated =
                            linkage.update(
                                    distances[left][other],
                                    distances[right][other],
                                    closest.distance(),
                                    clusterSizes[left],
                                    clusterSizes[right]);
                    distances[nextNode][other] = updated;
                    distances[other][nextNode] = updated;
                }
            }

            merges.add(new Merge(left, right, closest.distance(), mergedSize));
            active[left] = false;
            active[right] = false;
            active[nextNode] = true;
            clusterSizes[nextNode] = mergedSize;
            nextNode++;
        }
        return List.copyOf(merges);
    }

    private static Pair findClosestPair(boolean[] active, double[][] distances) {
        Pair closest = null;
        for (int left = 0; left < active.length; left++) {
            if (!active[left]) {
                continue;
            }
            for (int right = left + 1; right < active.length; right++) {
                if (!active[right]) {
                    continue;
                }
                double distance = distances[left][right];
                if (!Double.isFinite(distance)
                        || distance < -TOLERANCE
                        || distance > 1.0 + TOLERANCE) {
                    throw new IllegalStateException("Active cluster distance is invalid.");
                }
                if (closest == null || distance < closest.distance()) {
                    closest = new Pair(left, right, Math.max(0.0, Math.min(1.0, distance)));
                }
            }
        }
        if (closest == null) {
            throw new IllegalStateException("At least two active clusters are required to merge.");
        }
        return closest;
    }

    private record Pair(int left, int right, double distance) {}
}
