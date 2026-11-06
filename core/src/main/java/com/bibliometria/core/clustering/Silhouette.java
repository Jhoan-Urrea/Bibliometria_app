package com.bibliometria.core.clustering;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Computes mean and per-document silhouette scores from a validated distance matrix. */
public final class Silhouette {

    public static final int MIN_CLUSTERS = 2;
    public static final int MAX_CLUSTERS = 10;

    private Silhouette() {}

    /**
     * Returns the arithmetic mean of document silhouette values.
     *
     * <p>For each document {@code x}, cohesion {@code a(x)} is its mean distance to the other
     * documents in its own cluster, and separation {@code b(x)} is the smallest mean distance to
     * any other cluster. Its silhouette is {@code s(x)=(b(x)-a(x))/max(a(x),b(x))}. Singleton
     * clusters and the simultaneous-zero case are assigned {@code s(x)=0}.
     *
     * @param delta validated pairwise distance matrix
     * @param labels cluster label for each canonical matrix row
     * @return mean silhouette, in {@code [-1,1]}
     * @throws NullPointerException if either argument is null
     * @throws IllegalArgumentException if labels do not match the matrix or cluster count is not
     *     between 2 and 10
     */
    public static double score(DistanceMatrix delta, int[] labels) {
        double[] scores = perDocumentScores(delta, labels);
        double sum = 0.0;
        for (double score : scores) {
            sum += score;
        }
        return sum / scores.length;
    }

    /**
     * Returns each document's silhouette in canonical matrix order.
     *
     * @param delta validated pairwise distance matrix
     * @param labels cluster label for each canonical matrix row
     * @return a new array of per-document silhouette values
     */
    public static double[] perDocumentScores(DistanceMatrix delta, int[] labels) {
        Objects.requireNonNull(delta, "delta");
        Objects.requireNonNull(labels, "labels");
        delta.validar();
        if (labels.length != delta.size()) {
            throw new IllegalArgumentException("There must be one cluster label per document.");
        }

        Map<Integer, Integer> clusterIndexByLabel = new HashMap<>();
        for (int label : labels) {
            if (label < 0) {
                throw new IllegalArgumentException("Cluster labels must be non-negative.");
            }
            clusterIndexByLabel.computeIfAbsent(label, ignored -> clusterIndexByLabel.size());
        }
        int clusterCount = clusterIndexByLabel.size();
        if (clusterCount < MIN_CLUSTERS || clusterCount > MAX_CLUSTERS) {
            throw new IllegalArgumentException("Silhouette requires between 2 and 10 clusters.");
        }

        int[] clusterIndex = new int[labels.length];
        int[] clusterSizes = new int[clusterCount];
        for (int document = 0; document < labels.length; document++) {
            clusterIndex[document] = clusterIndexByLabel.get(labels[document]);
            clusterSizes[clusterIndex[document]]++;
        }

        double[][] distanceSums = new double[labels.length][clusterCount];
        for (int document = 0; document < labels.length; document++) {
            for (int other = 0; other < labels.length; other++) {
                if (document != other) {
                    distanceSums[document][clusterIndex[other]] += delta.distance(document, other);
                }
            }
        }

        double[] scores = new double[labels.length];
        for (int document = 0; document < labels.length; document++) {
            int ownCluster = clusterIndex[document];
            int ownClusterSize = clusterSizes[ownCluster];
            if (ownClusterSize == 1) {
                continue;
            }
            double cohesion = distanceSums[document][ownCluster] / (ownClusterSize - 1);
            double separation = Double.POSITIVE_INFINITY;
            for (int cluster = 0; cluster < clusterCount; cluster++) {
                if (cluster != ownCluster) {
                    double meanDistance = distanceSums[document][cluster] / clusterSizes[cluster];
                    separation = Math.min(separation, meanDistance);
                }
            }
            double denominator = Math.max(cohesion, separation);
            scores[document] = denominator == 0.0 ? 0.0 : (separation - cohesion) / denominator;
        }
        return scores;
    }
}
