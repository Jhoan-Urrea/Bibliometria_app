package com.bibliometria.core.similarity;

import com.bibliometria.core.text.ProcessedDocument;
import java.util.Objects;

/**
 * Similitud Levenshtein entre los puntos de código de los textos normalizados.
 *
 * <p>Mapeo fórmula-código: la tabla de prefijos usa inserción, eliminación y sustitución de costo
 * uno; el valor crudo es {@code E}, y {@code normalizado = 1 - E / max(|A|, |B|)}.
 */
public final class LevenshteinSimilarity implements SimilarityAlgorithm {

    /**
     * Calcula {@code 1 - E / max(|A|, |B|)}, donde {@code E} es la distancia de edición mínima
     * sobre puntos de código Unicode.
     *
     * @param documentA primer documento normalizado
     * @param documentB segundo documento normalizado
     * @return distancia cruda {@code E} y similitud normalizada en {@code [0, 1]}
     */
    @Override
    public SimilarityResult calcular(ProcessedDocument documentA, ProcessedDocument documentB) {
        Objects.requireNonNull(documentA, "documentA");
        Objects.requireNonNull(documentB, "documentB");
        String textA = documentA.textoNormalizado();
        String textB = documentB.textoNormalizado();
        int distance = distance(textA, textB);
        int maximumLength =
                Math.max(
                        textA.codePointCount(0, textA.length()),
                        textB.codePointCount(0, textB.length()));
        double similarity = maximumLength == 0 ? 1.0 : 1.0 - (double) distance / maximumLength;
        return new SimilarityResult(distance, similarity);
    }

    private int distance(String textA, String textB) {
        int[] codePointsA = textA.codePoints().toArray();
        int[] codePointsB = textB.codePoints().toArray();
        if (codePointsA.length < codePointsB.length) {
            return distance(textB, textA);
        }
        int[] previous = new int[codePointsB.length + 1];
        int[] current = new int[codePointsB.length + 1];
        for (int column = 0; column <= codePointsB.length; column++) {
            previous[column] = column;
        }
        for (int row = 1; row <= codePointsA.length; row++) {
            current[0] = row;
            for (int column = 1; column <= codePointsB.length; column++) {
                int substitutionCost = codePointsA[row - 1] == codePointsB[column - 1] ? 0 : 1;
                current[column] =
                        Math.min(
                                Math.min(current[column - 1] + 1, previous[column] + 1),
                                previous[column - 1] + substitutionCost);
            }
            int[] previousRow = previous;
            previous = current;
            current = previousRow;
        }
        return previous[codePointsB.length];
    }
}
