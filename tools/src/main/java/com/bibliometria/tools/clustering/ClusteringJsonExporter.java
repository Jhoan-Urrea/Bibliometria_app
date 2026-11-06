package com.bibliometria.tools.clustering;

import com.bibliometria.core.clustering.ClusterCut;
import com.bibliometria.core.clustering.ClusteringEvaluationService.Evaluation;
import com.bibliometria.core.clustering.ClusteringEvaluationService.LinkageEvaluation;
import com.bibliometria.core.clustering.Dendrogram.Node;
import com.bibliometria.core.clustering.DistanceMatrix;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Serializes evaluated hierarchies as JSON suitable for a D3.js dendrogram view. */
public final class ClusteringJsonExporter {

    private final ObjectMapper objectMapper;

    public ClusteringJsonExporter() {
        this(new ObjectMapper());
    }

    ClusteringJsonExporter(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * Builds a JSON tree with metrics, merge rows, canonical leaves, and nested D3-compatible
     * hierarchy nodes for all linkage candidates.
     *
     * @param evaluation metrics and hierarchies returned by the evaluation service
     * @param matrix source distance matrix
     * @param metadata title and authors indexed by canonical document ID
     * @return a mutable Jackson tree owned by the caller
     * @throws IllegalArgumentException if metadata does not cover exactly the matrix IDs
     */
    public ObjectNode toJson(
            Evaluation evaluation, DistanceMatrix matrix, List<ArticleMetadata> metadata) {
        Objects.requireNonNull(evaluation, "evaluation");
        Objects.requireNonNull(matrix, "matrix");
        Objects.requireNonNull(metadata, "metadata");
        matrix.validar();

        Map<String, ArticleMetadata> metadataById = indexMetadata(matrix, metadata);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("formatVersion", "1.0");
        root.put("decisionRule", "max silhouette; within 1e-9, max CPCC");
        root.put("selectedLinkage", evaluation.winner().linkage());
        root.put("selectedK", evaluation.winner().bestK());
        root.put("selectedSilhouette", evaluation.winner().bestSilhouette());
        root.put("selectedCpcc", evaluation.winner().cpcc());

        ArrayNode candidates = root.putArray("candidates");
        for (LinkageEvaluation candidate : evaluation.linkages()) {
            candidates.add(candidateNode(candidate, matrix, metadataById));
        }
        return root;
    }

    /**
     * Writes the complete evaluation as UTF-8 pretty-printed JSON.
     *
     * @param output destination JSON file
     * @param evaluation evaluation output
     * @param matrix original distance matrix
     * @param metadata title and authors indexed by document ID
     * @throws IOException if the destination cannot be written
     */
    public void write(
            Path output,
            Evaluation evaluation,
            DistanceMatrix matrix,
            List<ArticleMetadata> metadata)
            throws IOException {
        Objects.requireNonNull(output, "output");
        ObjectNode document = toJson(evaluation, matrix, metadata);
        Path absoluteOutput = output.toAbsolutePath();
        Path parent = absoluteOutput.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream stream = Files.newOutputStream(absoluteOutput)) {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(stream, document);
        }
    }

    private ObjectNode candidateNode(
            LinkageEvaluation candidate,
            DistanceMatrix matrix,
            Map<String, ArticleMetadata> metadataById) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("linkage", candidate.linkage());
        node.put("cpcc", candidate.cpcc());
        node.put("bestK", candidate.bestK());
        node.put("bestSilhouette", candidate.bestSilhouette());

        ObjectNode scores = node.putObject("silhouetteByK");
        candidate.silhouetteByK().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> scores.put(Integer.toString(entry.getKey()), entry.getValue()));

        ArrayNode merges = node.putArray("merges");
        candidate
                .merges()
                .forEach(
                        merge -> {
                            ObjectNode mergeNode = merges.addObject();
                            mergeNode.put("leftChild", merge.leftChild());
                            mergeNode.put("rightChild", merge.rightChild());
                            mergeNode.put("height", merge.height());
                            mergeNode.put("size", merge.size());
                        });

        ArrayNode leafOrder = node.putArray("leafOrder");
        for (String id : candidate.dendrogram().leafOrder()) {
            ArticleMetadata article = metadataById.get(id);
            ObjectNode leaf = leafOrder.addObject();
            leaf.put("id", id);
            leaf.put("title", article.title());
            leaf.put("authors", article.authors());
        }

        int[] labels = ClusterCut.cut(candidate.merges(), candidate.bestK(), matrix.size());
        Map<String, Integer> clusterById = new HashMap<>();
        for (int index = 0; index < matrix.size(); index++) {
            clusterById.put(matrix.documentIds().get(index), labels[index]);
        }
        node.set(
                "tree",
                treeNode(
                        candidate.dendrogram().root(),
                        metadataById,
                        clusterById,
                        new int[] {matrix.size()}));
        return node;
    }

    private ObjectNode treeNode(
            Node source,
            Map<String, ArticleMetadata> metadataById,
            Map<String, Integer> clusterById,
            int[] nextInternalId) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("height", source.height());
        node.put("size", source.size());
        if (source.isLeaf()) {
            String id = source.documentId();
            ArticleMetadata article = metadataById.get(id);
            node.put("id", id);
            node.put("documentId", id);
            node.put("name", article.title());
            node.put("title", article.title());
            node.put("authors", article.authors());
            node.put("cluster", clusterById.get(id));
            return node;
        }

        node.put("id", "cluster-" + nextInternalId[0]++);
        ArrayNode children = node.putArray("children");
        children.add(
                treeNode(source.left().orElseThrow(), metadataById, clusterById, nextInternalId));
        children.add(
                treeNode(source.right().orElseThrow(), metadataById, clusterById, nextInternalId));
        return node;
    }

    private static Map<String, ArticleMetadata> indexMetadata(
            DistanceMatrix matrix, List<ArticleMetadata> metadata) {
        Map<String, ArticleMetadata> byId = new HashMap<>();
        for (ArticleMetadata article : metadata) {
            Objects.requireNonNull(article, "article metadata");
            if (byId.putIfAbsent(article.id(), article) != null) {
                throw new IllegalArgumentException("Article metadata IDs must be unique.");
            }
        }
        if (byId.size() != matrix.size() || !byId.keySet().containsAll(matrix.documentIds())) {
            throw new IllegalArgumentException(
                    "Metadata must cover exactly the matrix document IDs.");
        }
        return Map.copyOf(byId);
    }

    /** Minimal corpus metadata included in tree leaves and display order. */
    public record ArticleMetadata(String id, String title, String authors) {

        public ArticleMetadata {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(authors, "authors");
            if (id.isBlank() || title.isBlank() || authors.isBlank()) {
                throw new IllegalArgumentException(
                        "Document ID, title, and authors must not be blank.");
            }
        }
    }
}
