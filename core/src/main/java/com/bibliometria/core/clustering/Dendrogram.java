package com.bibliometria.core.clustering;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable binary dendrogram built from the canonical leaves and agglomeration merges.
 *
 * <p>Each merge creates one internal node. Its height is the merge distance and its size is the
 * number of leaves below it. The left-to-right orientation is canonical: the child with the
 * smallest leaf index is placed first. The cophenetic matrix is derived by assigning each
 * cross-child leaf pair the height of its lowest common ancestor, in {@code O(N^2)} total time.
 */
public final class Dendrogram {

    private static final double HEIGHT_TOLERANCE = 1e-12;

    private final Node root;
    private final List<String> documentIds;
    private final CopheneticMatrix copheneticMatrix;

    private Dendrogram(Node root, List<String> documentIds) {
        this.root = root;
        this.documentIds = List.copyOf(documentIds);
        double[][] heights = new double[documentIds.size()][documentIds.size()];
        assignCopheneticHeights(root, heights);
        this.copheneticMatrix = new CopheneticMatrix(documentIds, heights);
    }

    /**
     * Reconstructs a tree from the leaf order of {@code matrix} and the ordered clustering merges.
     *
     * <p>Merge child IDs follow {@link AgglomerativeClustering}: the initial leaves are indexed
     * {@code 0..N-1}, and merge {@code k} creates node {@code N+k}. The list must describe one
     * complete binary tree, with each child active when referenced and with consistent sizes and
     * nondecreasing heights.
     *
     * @param matrix source matrix, whose IDs define the canonical leaf indices
     * @param merges agglomeration sequence
     * @return the immutable dendrogram
     * @throws NullPointerException if either argument is null or a merge is null
     * @throws IllegalArgumentException if the sequence does not form a complete valid tree
     */
    public static Dendrogram from(DistanceMatrix matrix, List<Merge> merges) {
        Objects.requireNonNull(matrix, "matrix");
        Objects.requireNonNull(merges, "merges");
        matrix.validar();

        int leafCount = matrix.size();
        if (merges.size() != leafCount - 1) {
            throw new IllegalArgumentException(
                    "A complete dendrogram requires exactly N-1 merges.");
        }

        int nodeCount = Math.subtractExact(Math.multiplyExact(leafCount, 2), 1);
        Node[] nodes = new Node[nodeCount];
        boolean[] active = new boolean[nodeCount];
        for (int index = 0; index < leafCount; index++) {
            nodes[index] = Node.leaf(index, matrix.documentIds().get(index));
            active[index] = true;
        }

        for (int mergeIndex = 0; mergeIndex < merges.size(); mergeIndex++) {
            Merge merge = Objects.requireNonNull(merges.get(mergeIndex), "merge");
            int nodeIndex = leafCount + mergeIndex;
            int leftIndex = merge.leftChild();
            int rightIndex = merge.rightChild();
            if (rightIndex >= nodeIndex
                    || !active[leftIndex]
                    || !active[rightIndex]
                    || leftIndex == rightIndex) {
                throw new IllegalArgumentException(
                        "Merge must reference two distinct active nodes.");
            }

            Node first = nodes[leftIndex];
            Node second = nodes[rightIndex];
            int expectedSize = Math.addExact(first.size(), second.size());
            if (merge.size() != expectedSize) {
                throw new IllegalArgumentException("Merge size does not match its child subtrees.");
            }
            double childHeight = Math.max(first.height(), second.height());
            if (merge.height() + HEIGHT_TOLERANCE < childHeight) {
                throw new IllegalArgumentException(
                        "Merge heights must be nondecreasing along paths.");
            }

            Node left = first.minimumLeafIndex() < second.minimumLeafIndex() ? first : second;
            Node right = left == first ? second : first;
            nodes[nodeIndex] =
                    Node.branch(left, right, Math.max(merge.height(), childHeight), expectedSize);
            active[leftIndex] = false;
            active[rightIndex] = false;
            active[nodeIndex] = true;
        }

        int rootIndex = nodeCount - 1;
        if (!active[rootIndex]) {
            throw new IllegalArgumentException("Merges do not produce one complete tree.");
        }
        return new Dendrogram(nodes[rootIndex], matrix.documentIds());
    }

    /** Returns the root node of the tree. */
    public Node root() {
        return root;
    }

    /** Returns immutable document IDs in the source matrix's canonical order. */
    public List<String> documentIds() {
        return documentIds;
    }

    /** Returns immutable leaf IDs in canonical, ascending document-ID order. */
    public List<String> leafOrder() {
        return documentIds;
    }

    /** Returns the immutable matrix of pairwise lowest-common-ancestor fusion heights. */
    public CopheneticMatrix copheneticMatrix() {
        return copheneticMatrix;
    }

    private static int[] assignCopheneticHeights(Node node, double[][] heights) {
        if (node.isLeaf()) {
            return new int[] {node.canonicalLeafIndex()};
        }

        int[] leftLeaves = assignCopheneticHeights(node.left().orElseThrow(), heights);
        int[] rightLeaves = assignCopheneticHeights(node.right().orElseThrow(), heights);
        for (int left : leftLeaves) {
            for (int right : rightLeaves) {
                heights[left][right] = node.height();
                heights[right][left] = node.height();
            }
        }

        int[] combined = new int[leftLeaves.length + rightLeaves.length];
        System.arraycopy(leftLeaves, 0, combined, 0, leftLeaves.length);
        System.arraycopy(rightLeaves, 0, combined, leftLeaves.length, rightLeaves.length);
        return combined;
    }

    /** Immutable leaf or internal node in a dendrogram. */
    public static final class Node {

        private final int leafIndex;
        private final String documentId;
        private final Node left;
        private final Node right;
        private final double height;
        private final int size;
        private final int minimumLeafIndex;

        private Node(
                int leafIndex,
                String documentId,
                Node left,
                Node right,
                double height,
                int size,
                int minimumLeafIndex) {
            this.leafIndex = leafIndex;
            this.documentId = documentId;
            this.left = left;
            this.right = right;
            this.height = height;
            this.size = size;
            this.minimumLeafIndex = minimumLeafIndex;
        }

        private static Node leaf(int index, String documentId) {
            return new Node(index, documentId, null, null, 0.0, 1, index);
        }

        private static Node branch(Node left, Node right, double height, int size) {
            return new Node(
                    -1,
                    null,
                    left,
                    right,
                    height,
                    size,
                    Math.min(left.minimumLeafIndex, right.minimumLeafIndex));
        }

        /** Returns whether this node represents one original document. */
        public boolean isLeaf() {
            return documentId != null;
        }

        /**
         * Returns the document ID for a leaf node.
         *
         * @throws IllegalStateException if this node is an internal merge
         */
        public String documentId() {
            if (!isLeaf()) {
                throw new IllegalStateException("Internal nodes do not have a document ID.");
            }
            return documentId;
        }

        /** Returns the zero-based canonical leaf index; internal nodes have no leaf index. */
        public Optional<Integer> leafIndex() {
            return isLeaf() ? Optional.of(leafIndex) : Optional.empty();
        }

        /** Returns the left child, or empty for a leaf. */
        public Optional<Node> left() {
            return Optional.ofNullable(left);
        }

        /** Returns the right child, or empty for a leaf. */
        public Optional<Node> right() {
            return Optional.ofNullable(right);
        }

        /** Returns this node's fusion height; leaves have height zero. */
        public double height() {
            return height;
        }

        /** Returns the number of original documents below this node. */
        public int size() {
            return size;
        }

        private int minimumLeafIndex() {
            return minimumLeafIndex;
        }

        private int canonicalLeafIndex() {
            return leafIndex;
        }
    }
}
