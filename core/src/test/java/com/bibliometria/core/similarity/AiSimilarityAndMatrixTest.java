package com.bibliometria.core.similarity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.bibliometria.core.text.ProcessedDocument;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AiSimilarityAndMatrixTest {

    @Test
    void calculatesNormalizedFastTextCosine() {
        ProcessedDocument documentA = document("A");
        ProcessedDocument documentB = document("B");
        Map<String, double[]> vectors = new LinkedHashMap<>();
        vectors.put("A", vector(300, 0, 1.0));
        vectors.put("B", vector(300, 1, 1.0));

        SimilarityResult result = new FastTextSimilarity(vectors).calcular(documentA, documentB);

        assertThat(result.crudo()).isCloseTo(0.0, within(1e-12));
        assertThat(result.normalizado()).isCloseTo(0.5, within(1e-12));
    }

    @Test
    void calculatesSentenceBertCosineSimilarity() {
        ProcessedDocument documentA = document("A");
        ProcessedDocument documentB = document("B");
        Map<String, double[]> embeddings = new LinkedHashMap<>();
        embeddings.put("A", vector(384, 0, 1.0));
        embeddings.put("B", vector(384, 1, 1.0));

        SimilarityResult result =
                new SentenceBertSimilarity(embeddings).calcular(documentA, documentB);

        assertThat(result.crudo()).isCloseTo(0.0, within(1e-12));
        assertThat(result.normalizado()).isCloseTo(0.5, within(1e-12));
    }

    @Test
    void rejectsInvalidAiVectorDimensionsAndNonUnitSentenceBertVectors() {
        assertThatThrownBy(() -> new FastTextSimilarity(Map.of("A", new double[299])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("300 dimensions");
        assertThatThrownBy(() -> new SentenceBertSimilarity(Map.of("A", new double[384])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unit L2 norm");
    }

    @Test
    void matrixServiceBuildsSquareSymmetricMatricesAndReusesCachedResults() {
        List<ProcessedDocument> documents = documents(20);
        AtomicInteger calculations = new AtomicInteger();
        SimilarityAlgorithm countingAlgorithm =
                (documentA, documentB) -> {
                    calculations.incrementAndGet();
                    return new SimilarityResult(0.5, 0.5);
                };
        SimilarityMatrixService service = new SimilarityMatrixService();
        Map<String, SimilarityAlgorithm> algorithms = Map.of("counted", countingAlgorithm);

        Map<String, double[][]> first = service.calculateMatrix(documents, algorithms);
        Map<String, double[][]> second = service.calculateMatrix(documents, algorithms);
        double[][] matrix = first.get("counted");

        assertThat(matrix).hasDimensions(20, 20);
        assertThat(second.get("counted")).hasDimensions(20, 20);
        for (int row = 0; row < matrix.length; row++) {
            assertThat(matrix[row][row]).isEqualTo(1.0);
            for (int column = 0; column < matrix.length; column++) {
                assertThat(matrix[row][column]).isEqualTo(matrix[column][row]);
            }
        }
        assertThat(service.cachedPairCount()).isEqualTo(190);
        assertThat(calculations).hasValue(190);
    }

    @Test
    void calculatesSixBySixPearsonCorrelationOverThe190Pairs() {
        Map<String, double[][]> matrices = new LinkedHashMap<>();
        for (int algorithm = 0; algorithm < 6; algorithm++) {
            double[][] matrix = new double[20][20];
            for (int document = 0; document < 20; document++) {
                matrix[document][document] = 1.0;
            }
            int pairIndex = 0;
            for (int row = 0; row < 20; row++) {
                for (int column = row + 1; column < 20; column++) {
                    double score = pairScore(algorithm, pairIndex++);
                    matrix[row][column] = score;
                    matrix[column][row] = score;
                }
            }
            matrices.put("algorithm-" + algorithm, matrix);
        }

        Map<String, Map<String, Double>> correlations = new RankCorrelation().calculate(matrices);

        assertThat(correlations).hasSize(6);
        assertThat(correlations.get("algorithm-0")).hasSize(6);
        assertThat(correlations.get("algorithm-0").get("algorithm-0"))
                .isCloseTo(1.0, within(1e-12));
        assertThat(correlations.get("algorithm-0").get("algorithm-1"))
                .isCloseTo(-1.0, within(1e-12));
        assertThat(correlations.get("algorithm-2").get("algorithm-4"))
                .isCloseTo(1.0, within(1e-12));
    }

    private static double pairScore(int algorithm, int pairIndex) {
        double score = pairIndex / 190.0;
        return algorithm == 1 ? 1.0 - score : score;
    }

    private static List<ProcessedDocument> documents(int count) {
        List<ProcessedDocument> documents = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            documents.add(document("D%02d".formatted(index)));
        }
        return List.copyOf(documents);
    }

    private static ProcessedDocument document(String id) {
        return new ProcessedDocument(id, "", List.of(), Set.of(), Map.of(), "");
    }

    private static double[] vector(int dimensions, int nonZeroIndex, double value) {
        double[] vector = new double[dimensions];
        vector[nonZeroIndex] = value;
        return vector;
    }

    @Nested
    class FastTextContract extends SimilarityContractTest {
        @Override
        protected SimilarityAlgorithm crearAlgoritmo() {
            Map<String, double[]> vectors = new LinkedHashMap<>();
            vectors.put(documentId("a b c"), vector(300, 0, 1.0));
            vectors.put(documentId("c b a"), vector(300, 1, 1.0));
            vectors.put(documentId("x y"), vector(300, 2, 1.0));
            return new FastTextSimilarity(vectors);
        }
    }

    @Nested
    class SentenceBertContract extends SimilarityContractTest {
        @Override
        protected SimilarityAlgorithm crearAlgoritmo() {
            Map<String, double[]> embeddings = new LinkedHashMap<>();
            embeddings.put(documentId("a b c"), vector(384, 0, 1.0));
            embeddings.put(documentId("c b a"), vector(384, 1, 1.0));
            embeddings.put(documentId("x y"), vector(384, 2, 1.0));
            return new SentenceBertSimilarity(embeddings);
        }
    }
}
