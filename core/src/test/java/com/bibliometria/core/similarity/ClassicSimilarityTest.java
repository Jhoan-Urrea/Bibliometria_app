package com.bibliometria.core.similarity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.bibliometria.core.text.ProcessedDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ClassicSimilarityTest {

    @ParameterizedTest
    @MethodSource("levenshteinCases")
    void calculatesLevenshteinSimilarity(
            String textA, String textB, int expectedDistance, double expectedSimilarity) {
        SimilarityResult result =
                new LevenshteinSimilarity()
                        .calcular(document("A", textA, List.of()), document("B", textB, List.of()));

        assertThat(result.crudo()).isEqualTo(expectedDistance);
        assertThat(result.normalizado()).isCloseTo(expectedSimilarity, within(1e-12));
    }

    private static Stream<Arguments> levenshteinCases() {
        return Stream.of(Arguments.of("kitten", "sitting", 3, 4.0 / 7.0));
    }

    @ParameterizedTest
    @MethodSource("lcsCases")
    void reconstructsLongestCommonTokenSubsequence(
            List<String> tokensA, List<String> tokensB, List<String> expectedSubsequence) {
        LcsSimilarity similarity = new LcsSimilarity();
        ProcessedDocument documentA = document("A", String.join(" ", tokensA), tokensA);
        ProcessedDocument documentB = document("B", String.join(" ", tokensB), tokensB);

        List<String> actualSubsequence = similarity.longestCommonSubsequence(documentA, documentB);
        SimilarityResult result = similarity.calcular(documentA, documentB);

        assertThat(actualSubsequence).containsExactlyElementsOf(expectedSubsequence);
        assertThat(result.crudo()).isEqualTo(expectedSubsequence.size());
        assertThat(result.normalizado())
                .isEqualTo(
                        (double) expectedSubsequence.size()
                                / Math.max(tokensA.size(), tokensB.size()));
    }

    private static Stream<Arguments> lcsCases() {
        return Stream.of(
                Arguments.of(
                        List.of("A", "B", "C", "B", "D", "A", "B"),
                        List.of("B", "D", "C", "A", "B", "A"),
                        List.of("B", "C", "B", "A")));
    }

    @ParameterizedTest
    @MethodSource("tfIdfCases")
    void calculatesTfIdfCosineSimilarity(
            List<ProcessedDocument> corpus,
            ProcessedDocument documentA,
            ProcessedDocument documentB,
            double expectedCosine) {
        SimilarityResult result = new TfIdfSimilarity(corpus).calcular(documentA, documentB);

        assertThat(result.crudo()).isCloseTo(expectedCosine, within(1e-12));
        assertThat(result.normalizado()).isCloseTo(expectedCosine, within(1e-12));
    }

    private static Stream<Arguments> tfIdfCases() {
        ProcessedDocument corpusA = document("C1", "", List.of("ai", "ai", "education"));
        ProcessedDocument corpusB = document("C2", "", List.of("ai", "learning"));
        ProcessedDocument corpusC = document("C3", "", List.of("ethics", "learning"));
        List<ProcessedDocument> corpus = new ArrayList<>(List.of(corpusA, corpusB, corpusC));
        for (int index = 3; index < 20; index++) {
            corpus.add(document("C" + (index + 1), "", List.of()));
        }
        double log10 = Math.log(10.0);
        double log20 = Math.log(20.0);
        double expected =
                2.0
                        * log10
                        * log10
                        / Math.sqrt((4.0 * log10 * log10 + log20 * log20) * (2.0 * log10 * log10));
        return Stream.of(Arguments.of(List.copyOf(corpus), corpusA, corpusB, expected));
    }

    @ParameterizedTest
    @MethodSource("jaccardCases")
    void calculatesJaccardCoefficient(
            Set<String> tokensA, Set<String> tokensB, double expectedSimilarity) {
        ProcessedDocument documentA =
                document("A", String.join(" ", tokensA), List.copyOf(tokensA));
        ProcessedDocument documentB =
                document("B", String.join(" ", tokensB), List.copyOf(tokensB));

        SimilarityResult result = new JaccardSimilarity().calcular(documentA, documentB);

        assertThat(result.crudo()).isCloseTo(expectedSimilarity, within(1e-12));
        assertThat(result.normalizado()).isCloseTo(expectedSimilarity, within(1e-12));
    }

    private static Stream<Arguments> jaccardCases() {
        return Stream.of(Arguments.of(Set.of("cat", "dog"), Set.of("dog", "bird"), 1.0 / 3.0));
    }

    private static ProcessedDocument document(String id, String text, List<String> tokens) {
        Map<String, Integer> frequencies =
                tokens.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        token -> token, token -> 1, Integer::sum));
        return new ProcessedDocument(
                id,
                text,
                tokens,
                Set.copyOf(frequencies.keySet()),
                frequencies,
                String.join(" ", tokens));
    }

    @Nested
    class LevenshteinContract extends SimilarityContractTest {
        @Override
        protected SimilarityAlgorithm crearAlgoritmo() {
            return new LevenshteinSimilarity();
        }
    }

    @Nested
    class LcsContract extends SimilarityContractTest {
        @Override
        protected SimilarityAlgorithm crearAlgoritmo() {
            return new LcsSimilarity();
        }
    }

    @Nested
    class JaccardContract extends SimilarityContractTest {
        @Override
        protected SimilarityAlgorithm crearAlgoritmo() {
            return new JaccardSimilarity();
        }
    }

    @Nested
    class TfIdfContract extends SimilarityContractTest {
        private final List<ProcessedDocument> globalCorpus = globalCorpus();

        @Override
        protected SimilarityAlgorithm crearAlgoritmo() {
            return new TfIdfSimilarity(globalCorpus);
        }

        private List<ProcessedDocument> globalCorpus() {
            List<ProcessedDocument> corpus = new java.util.ArrayList<>();
            corpus.add(ClassicSimilarityTest.document("C1", "", List.of("a", "b", "c", "x", "y")));
            for (int index = 1; index < 20; index++) {
                corpus.add(ClassicSimilarityTest.document("C" + (index + 1), "", List.of()));
            }
            return List.copyOf(corpus);
        }
    }

    @Test
    void requiresTheCompleteTwentyDocumentCorpusForTfIdf() {
        assertThat(
                        org.assertj.core.api.Assertions.catchThrowable(
                                () ->
                                        new TfIdfSimilarity(
                                                List.of(document("C1", "", List.of("token"))))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly 20 documents");
    }
}
