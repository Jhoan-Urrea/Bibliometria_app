package com.bibliometria.core.clustering;

import java.util.List;
import java.util.Objects;

/** Cuts a complete agglomeration history into a requested number of flat clusters. */
public final class ClusterCut {

    private ClusterCut() {}

    /**
     * Cuts the hierarchy at {@code k} clusters and returns one label per canonical leaf.
     *
     * <p>The first {@code numLeaves-k} merges are retained and the final {@code k-1} merges are
     * omitted. A union-find structure tracks the retained components. Labels are assigned
     * consecutively from {@code 0} to {@code k-1}, ordered by each cluster's smallest leaf index.
     *
     * @param merges complete merge history from {@link AgglomerativeClustering#run}
     * @param k desired cluster count, in {@code [1,numLeaves]}
     * @param numLeaves number of leaves in the hierarchy
     * @return labels indexed by canonical leaf index
     * @throws NullPointerException if {@code merges} or any merge is null
     * @throws IllegalArgumentException if the dimensions, cut, or merge history is invalid
     */
    public static int[] cut(List<Merge> merges, int k, int numLeaves) {
        Objects.requireNonNull(merges, "merges");
        if (numLeaves < 1 || k < 1 || k > numLeaves) {
            throw new IllegalArgumentException("Require 1 <= k <= numLeaves and numLeaves >= 1.");
        }
        if (merges.size() != numLeaves - 1) {
            throw new IllegalArgumentException("A complete hierarchy requires numLeaves-1 merges.");
        }

        int nodeCount = Math.subtractExact(Math.multiplyExact(numLeaves, 2), 1);
        boolean[] active = new boolean[nodeCount];
        int[] sizes = new int[nodeCount];
        int[] representative = new int[nodeCount];
        for (int leaf = 0; leaf < numLeaves; leaf++) {
            active[leaf] = true;
            sizes[leaf] = 1;
            representative[leaf] = leaf;
        }

        UnionFind unionFind = new UnionFind(numLeaves);
        int retainedMergeCount = numLeaves - k;
        for (int index = 0; index < merges.size(); index++) {
            Merge merge = Objects.requireNonNull(merges.get(index), "merge");
            int currentNode = numLeaves + index;
            int left = merge.leftChild();
            int right = merge.rightChild();
            if (right >= currentNode || !active[left] || !active[right]) {
                throw new IllegalArgumentException(
                        "Merge must reference two distinct active nodes.");
            }

            int mergedSize = Math.addExact(sizes[left], sizes[right]);
            if (merge.size() != mergedSize) {
                throw new IllegalArgumentException("Merge size does not match its child clusters.");
            }
            if (index < retainedMergeCount) {
                representative[currentNode] =
                        unionFind.union(representative[left], representative[right]);
            }
            sizes[currentNode] = mergedSize;
            active[left] = false;
            active[right] = false;
            active[currentNode] = true;
        }

        int[] minimumLeafByRoot = new int[numLeaves];
        java.util.Arrays.fill(minimumLeafByRoot, Integer.MAX_VALUE);
        for (int leaf = 0; leaf < numLeaves; leaf++) {
            int root = unionFind.find(leaf);
            minimumLeafByRoot[root] = Math.min(minimumLeafByRoot[root], leaf);
        }

        int[] labelByRoot = new int[numLeaves];
        int nextLabel = 0;
        for (int leaf = 0; leaf < numLeaves; leaf++) {
            int root = unionFind.find(leaf);
            if (minimumLeafByRoot[root] == leaf) {
                labelByRoot[root] = nextLabel++;
            }
        }
        if (nextLabel != k) {
            throw new IllegalArgumentException("Merge history does not produce the requested cut.");
        }

        int[] labels = new int[numLeaves];
        for (int leaf = 0; leaf < numLeaves; leaf++) {
            labels[leaf] = labelByRoot[unionFind.find(leaf)];
        }
        return labels;
    }

    private static final class UnionFind {

        private final int[] parent;
        private final byte[] rank;

        private UnionFind(int size) {
            parent = new int[size];
            rank = new byte[size];
            for (int index = 0; index < size; index++) {
                parent[index] = index;
            }
        }

        private int find(int value) {
            if (parent[value] != value) {
                parent[value] = find(parent[value]);
            }
            return parent[value];
        }

        private int union(int first, int second) {
            int rootFirst = find(first);
            int rootSecond = find(second);
            if (rootFirst == rootSecond) {
                throw new IllegalArgumentException("Merge attempts to join overlapping clusters.");
            }
            if (rank[rootFirst] < rank[rootSecond]) {
                parent[rootFirst] = rootSecond;
                return rootSecond;
            }
            if (rank[rootFirst] > rank[rootSecond]) {
                parent[rootSecond] = rootFirst;
                return rootFirst;
            }
            parent[rootSecond] = rootFirst;
            rank[rootFirst]++;
            return rootFirst;
        }
    }
}
