package com.bibliometria.core.similarity;

import com.bibliometria.core.text.ProcessedDocument;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Similitud LCS sobre secuencias de tokens.
 *
 * <p>Mapeo fórmula-código: la tabla calcula {@code L(i,j)} por tabulación y el retroceso
 * reconstruye una subsecuencia; el valor crudo es {@code L}, y {@code normalizado = L / max(|A|,
 * |B|)}.
 */
public final class LcsSimilarity implements SimilarityAlgorithm {

    @Override
    public SimilarityResult calcular(ProcessedDocument documentA, ProcessedDocument documentB) {
        List<String> subsequence = longestCommonSubsequence(documentA, documentB);
        int maximumLength = Math.max(documentA.tokens().size(), documentB.tokens().size());
        double similarity = maximumLength == 0 ? 1.0 : (double) subsequence.size() / maximumLength;
        return new SimilarityResult(subsequence.size(), similarity);
    }

    /**
     * Reconstruye una subsecuencia común mediante retroceso desde la tabla de programación
     * dinámica.
     *
     * @param documentA primer documento
     * @param documentB segundo documento
     * @return subsecuencia común de tokens en su orden original
     */
    public List<String> longestCommonSubsequence(
            ProcessedDocument documentA, ProcessedDocument documentB) {
        Objects.requireNonNull(documentA, "documentA");
        Objects.requireNonNull(documentB, "documentB");
        List<String> tokensA = documentA.tokens();
        List<String> tokensB = documentB.tokens();
        int[][] table = new int[tokensA.size() + 1][tokensB.size() + 1];

        for (int row = 1; row <= tokensA.size(); row++) {
            for (int column = 1; column <= tokensB.size(); column++) {
                table[row][column] =
                        tokensA.get(row - 1).equals(tokensB.get(column - 1))
                                ? table[row - 1][column - 1] + 1
                                : Math.max(table[row - 1][column], table[row][column - 1]);
            }
        }

        List<String> reversed = new ArrayList<>(table[tokensA.size()][tokensB.size()]);
        int row = tokensA.size();
        int column = tokensB.size();
        while (row > 0 && column > 0) {
            if (tokensA.get(row - 1).equals(tokensB.get(column - 1))) {
                reversed.add(tokensA.get(row - 1));
                row--;
                column--;
            } else if (table[row - 1][column] >= table[row][column - 1]) {
                row--;
            } else {
                column--;
            }
        }
        Collections.reverse(reversed);
        return List.copyOf(reversed);
    }
}
