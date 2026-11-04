package com.bibliometria.tools.clustering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bibliometria.core.clustering.ClusteringEvaluationService;
import com.bibliometria.core.clustering.DistanceMatrix;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClusteringJsonExporterTest {

    @TempDir Path tempDir;

    @Test
    void exportsMetricsMergesD3TreeAndLeafMetadataAsValidJson() throws Exception {
        DistanceMatrix matrix = fixtureMatrix();
        ClusteringEvaluationService.Evaluation evaluation =
                ClusteringEvaluationService.evaluate(matrix);
        List<ClusteringJsonExporter.ArticleMetadata> metadata =
                List.of(
                        new ClusteringJsonExporter.ArticleMetadata("A", "Title A", "Author A"),
                        new ClusteringJsonExporter.ArticleMetadata("B", "Title B", "Author B"),
                        new ClusteringJsonExporter.ArticleMetadata("C", "Title C", "Author C"),
                        new ClusteringJsonExporter.ArticleMetadata("D", "Title D", "Author D"));
        Path output = tempDir.resolve("reports/clustering.json");

        new ClusteringJsonExporter().write(output, evaluation, matrix, metadata);

        JsonNode document = new ObjectMapper().readTree(Files.readString(output));
        assertThat(document.get("formatVersion").asText()).isEqualTo("1.0");
        assertThat(document.get("selectedLinkage").asText())
                .isEqualTo(evaluation.winner().linkage());
        assertThat(document.get("selectedK").asInt()).isEqualTo(evaluation.winner().bestK());
        assertThat(document.get("candidates")).hasSize(3);
        for (JsonNode candidate : document.get("candidates")) {
            assertThat(candidate.get("merges")).hasSize(3);
            assertThat(candidate.get("silhouetteByK").size()).isEqualTo(3);
            assertThat(candidate.get("leafOrder")).hasSize(4);
            JsonNode tree = candidate.get("tree");
            assertThat(tree.get("children")).hasSize(2);
            assertThat(tree.findValue("title").asText()).startsWith("Title ");
            assertThat(tree.findValue("authors").asText()).startsWith("Author ");
            assertThat(tree.findValue("cluster")).isNotNull();
        }
    }

    @Test
    void rejectsIncompleteOrDuplicateArticleMetadata() {
        DistanceMatrix matrix = fixtureMatrix();
        ClusteringEvaluationService.Evaluation evaluation =
                ClusteringEvaluationService.evaluate(matrix);
        ClusteringJsonExporter exporter = new ClusteringJsonExporter();
        List<ClusteringJsonExporter.ArticleMetadata> incomplete =
                List.of(new ClusteringJsonExporter.ArticleMetadata("A", "Title A", "Author A"));

        assertThatThrownBy(() -> exporter.toJson(evaluation, matrix, incomplete))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cover exactly");

        List<ClusteringJsonExporter.ArticleMetadata> duplicate =
                List.of(
                        new ClusteringJsonExporter.ArticleMetadata("A", "Title A", "Author A"),
                        new ClusteringJsonExporter.ArticleMetadata("A", "Title A2", "Author A2"),
                        new ClusteringJsonExporter.ArticleMetadata("C", "Title C", "Author C"),
                        new ClusteringJsonExporter.ArticleMetadata("D", "Title D", "Author D"));
        assertThatThrownBy(() -> exporter.toJson(evaluation, matrix, duplicate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique");
    }

    @Test
    void commandProcessesAFullVerifiedCorpusAndWritesFrontendJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode corpus = mapper.createArrayNode();
        for (int index = 1; index <= 20; index++) {
            ObjectNode article = corpus.addObject();
            article.put("id", "A%02d".formatted(index));
            article.put("title", "Artificial intelligence education study " + index);
            article.put("authors", "Author " + index);
            article.put(
                    "abstractText",
                    "This verified abstract discusses artificial intelligence in education "
                            + "and learning analytics with documented bibliometric methods.");
            article.put("verified", true);
        }
        Path corpusFile = tempDir.resolve("corpus.json");
        Path output = tempDir.resolve("result/clustering.json");
        mapper.writeValue(corpusFile.toFile(), corpus);

        ClusteringExportCommand.run(corpusFile, output);

        JsonNode document = mapper.readTree(Files.readString(output));
        assertThat(document.get("candidates")).hasSize(3);
        assertThat(document.get("selectedK").asInt()).isBetween(2, 10);
        assertThat(document.get("candidates").get(0).get("leafOrder")).hasSize(20);
    }

    private static DistanceMatrix fixtureMatrix() {
        return DistanceMatrix.fromDistances(
                List.of("A", "B", "C", "D"),
                new double[][] {
                    {0.0, 0.1, 0.4, 0.7},
                    {0.1, 0.0, 0.5, 0.8},
                    {0.4, 0.5, 0.0, 0.3},
                    {0.7, 0.8, 0.3, 0.0}
                });
    }
}
