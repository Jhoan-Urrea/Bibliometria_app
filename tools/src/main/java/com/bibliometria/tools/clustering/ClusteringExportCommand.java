package com.bibliometria.tools.clustering;

import com.bibliometria.core.clustering.ClusteringEvaluationService;
import com.bibliometria.core.clustering.DistanceMatrix;
import com.bibliometria.core.text.Article;
import com.bibliometria.core.text.ProcessedDocument;
import com.bibliometria.core.text.TextPreprocessor;
import com.bibliometria.tools.clustering.ClusteringJsonExporter.ArticleMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Command-line entry point for evaluating the official corpus and exporting D3-ready JSON. */
public final class ClusteringExportCommand {

    private ClusteringExportCommand() {}

    /**
     * Loads a verified corpus JSON, evaluates all linkage criteria, and writes the JSON artifact.
     *
     * @param corpusPath input corpus JSON file
     * @param outputPath destination JSON file
     * @throws IOException if input parsing or output writing fails
     */
    public static void run(Path corpusPath, Path outputPath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode corpus;
        try (var input = Files.newInputStream(corpusPath)) {
            corpus = mapper.readTree(input);
        }
        if (corpus == null || !corpus.isArray() || corpus.isEmpty()) {
            throw new IOException("Corpus JSON must be a non-empty array.");
        }

        TextPreprocessor preprocessor = new TextPreprocessor();
        List<ProcessedDocument> documents = new ArrayList<>(corpus.size());
        List<ArticleMetadata> metadata = new ArrayList<>(corpus.size());
        for (JsonNode articleNode : corpus) {
            String id = requiredText(articleNode, "id");
            String title = requiredText(articleNode, "title");
            String authors = requiredText(articleNode, "authors");
            String abstractText = requiredText(articleNode, "abstractText");
            JsonNode verified = articleNode.get("verified");
            if (verified == null || !verified.isBoolean() || !verified.booleanValue()) {
                throw new IOException("Every corpus article must be verified: " + id);
            }
            Article article = new Article(id, title, authors, abstractText, true);
            documents.add(preprocessor.process(article));
            metadata.add(new ArticleMetadata(id, title, authors));
        }

        DistanceMatrix matrix = new DistanceMatrix(documents);
        ClusteringEvaluationService.Evaluation evaluation =
                ClusteringEvaluationService.evaluate(matrix);
        new ClusteringJsonExporter().write(outputPath, evaluation, matrix, metadata);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: ClusteringExportCommand <corpus.json> <clustering.json>");
        }
        run(Path.of(args[0]), Path.of(args[1]));
    }

    private static String requiredText(JsonNode article, String field) throws IOException {
        JsonNode value = article.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IOException("Every corpus article must contain non-blank " + field + ".");
        }
        return value.textValue();
    }
}
