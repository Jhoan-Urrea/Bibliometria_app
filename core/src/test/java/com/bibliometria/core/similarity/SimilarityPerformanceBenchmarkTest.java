package com.bibliometria.core.similarity;

import static org.assertj.core.api.Assertions.assertThat;

import com.bibliometria.core.text.ProcessedDocument;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SimilarityPerformanceBenchmarkTest {

    @Test
    void measuresLevenshteinAndLcsOnDeterministicLongTexts() {
        String textA = longText();
        String textB = textA.substring(0, textA.length() - 1) + "x";
        ProcessedDocument documentA = document("A01", textA);
        ProcessedDocument documentB = document("A02", textB);
        LevenshteinSimilarity levenshtein = new LevenshteinSimilarity();
        LcsSimilarity lcs = new LcsSimilarity();

        for (int warmup = 0; warmup < 5; warmup++) {
            levenshtein.calcular(documentA, documentB);
            lcs.calcular(documentA, documentB);
        }

        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = usedMemory(runtime);
        long levenshteinStart = System.nanoTime();
        SimilarityResult levenshteinResult = levenshtein.calcular(documentA, documentB);
        long levenshteinNanos = System.nanoTime() - levenshteinStart;
        long lcsStart = System.nanoTime();
        SimilarityResult lcsResult = lcs.calcular(documentA, documentB);
        long lcsNanos = System.nanoTime() - lcsStart;
        long memoryAfter = usedMemory(runtime);
        long levenshteinWorkingBytes =
                4L
                        * (textA.codePointCount(0, textA.length())
                                + textB.codePointCount(0, textB.length())
                                + 2L * (Math.min(textA.length(), textB.length()) + 1));
        long lcsWorkingBytes =
                4L * (documentA.tokens().size() + 1) * (documentB.tokens().size() + 1);

        assertThat(textA.length()).isGreaterThanOrEqualTo(1_500);
        assertThat(levenshteinResult.normalizado()).isBetween(0.0, 1.0);
        assertThat(lcsResult.normalizado()).isBetween(0.0, 1.0);
        System.out.printf(
                "Similarity benchmark: chars=%d, tokens=%d, Levenshtein=%.3f ms, "
                        + "LCS=%.3f ms, estimated working memory: Levenshtein=%d bytes, "
                        + "LCS=%d bytes, observed used-heap delta=%d bytes%n",
                textA.length(),
                documentA.tokens().size(),
                levenshteinNanos / 1_000_000.0,
                lcsNanos / 1_000_000.0,
                levenshteinWorkingBytes,
                lcsWorkingBytes,
                memoryAfter - memoryBefore);
    }

    private static String longText() {
        StringBuilder text = new StringBuilder(1_600);
        for (int index = 0; text.length() < 1_500; index++) {
            text.append("bibliometric similarity retrieval analysis topic ")
                    .append(index % 17)
                    .append(' ');
        }
        return text.substring(0, 1_500);
    }

    private static long usedMemory(Runtime runtime) {
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static ProcessedDocument document(String id, String text) {
        List<String> tokens = List.of(text.strip().split("\\s+"));
        LinkedHashMap<String, Integer> frequencies = new LinkedHashMap<>();
        tokens.forEach(token -> frequencies.merge(token, 1, Integer::sum));
        return new ProcessedDocument(
                id,
                text,
                tokens,
                Set.copyOf(frequencies.keySet()),
                frequencies,
                String.join(" ", tokens));
    }
}
