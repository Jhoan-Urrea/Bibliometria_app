package com.bibliometria.core.similarity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Calcula correlaciones de Pearson entre los rangos de similitud de cada algoritmo. */
public final class RankCorrelation {

    /** Número de algoritmos esperados en la matriz consolidada. */
    public static final int ALGORITHM_COUNT = 6;

    /** Número de pares únicos en un corpus de 20 documentos: {@code 20 * 19 / 2}. */
    public static final int PAIR_COUNT = 190;

    /**
     * Calcula una matriz 6x6 a partir de las matrices cuadradas del servicio, usando los 190 pares
     * fuera de diagonal del corpus de 20 documentos.
     *
     * @param similarityMatrices matriz cuadrada normalizada por nombre de algoritmo
     * @return matriz de correlación de Pearson entre los rangos promedio
     */
    public Map<String, Map<String, Double>> calculate(Map<String, double[][]> similarityMatrices) {
        Objects.requireNonNull(similarityMatrices, "similarityMatrices");
        if (similarityMatrices.size() != ALGORITHM_COUNT) {
            throw new IllegalArgumentException("Exactly six algorithms are required.");
        }

        Map<String, double[]> ranks = new LinkedHashMap<>();
        for (Map.Entry<String, double[][]> entry : similarityMatrices.entrySet()) {
            double[][] matrix = entry.getValue();
            validateMatrix(entry.getKey(), matrix);
            ranks.put(entry.getKey(), averageRanks(matrix));
        }

        Map<String, Map<String, Double>> correlations = new LinkedHashMap<>();
        for (String nameA : ranks.keySet()) {
            Map<String, Double> row = new LinkedHashMap<>();
            for (String nameB : ranks.keySet()) {
                row.put(nameB, pearson(ranks.get(nameA), ranks.get(nameB)));
            }
            correlations.put(nameA, Collections.unmodifiableMap(row));
        }
        return Collections.unmodifiableMap(correlations);
    }

    private void validateMatrix(String name, double[][] matrix) {
        if (name == null || name.isBlank() || matrix == null || matrix.length != 20) {
            throw new IllegalArgumentException(
                    "Each algorithm must provide a 20x20 matrix for the global corpus.");
        }
        for (int row = 0; row < matrix.length; row++) {
            if (matrix[row] == null || matrix[row].length != matrix.length) {
                throw new IllegalArgumentException("Similarity matrices must be square.");
            }
            if (!Double.isFinite(matrix[row][row]) || Math.abs(matrix[row][row] - 1.0) > 1e-12) {
                throw new IllegalArgumentException("Similarity matrix diagonals must equal 1.0.");
            }
            for (int column = row + 1; column < matrix.length; column++) {
                double value = matrix[row][column];
                if (!Double.isFinite(value)
                        || value < 0.0
                        || value > 1.0
                        || Math.abs(value - matrix[column][row]) > 1e-12) {
                    throw new IllegalArgumentException(
                            "Similarity matrices must be finite, normalized, and symmetric.");
                }
            }
        }
    }

    private double[] averageRanks(double[][] matrix) {
        List<PairScore> scores = new ArrayList<>(PAIR_COUNT);
        for (int row = 0; row < matrix.length; row++) {
            for (int column = row + 1; column < matrix.length; column++) {
                scores.add(new PairScore(row, column, matrix[row][column]));
            }
        }
        if (scores.size() != PAIR_COUNT) {
            throw new IllegalArgumentException("Exactly 190 document pairs are required.");
        }
        List<PairScore> ordered = new ArrayList<>(scores);
        ordered.sort((left, right) -> Double.compare(left.score(), right.score()));

        double[] ranks = new double[PAIR_COUNT];
        int start = 0;
        while (start < ordered.size()) {
            int end = start + 1;
            double score = ordered.get(start).score();
            while (end < ordered.size() && Double.compare(ordered.get(end).score(), score) == 0) {
                end++;
            }
            double averageRank = ((start + 1) + end) / 2.0;
            for (int index = start; index < end; index++) {
                PairScore pair = ordered.get(index);
                int pairIndex = pair.row() * 20 - pair.row() * (pair.row() + 1) / 2
                        + pair.column() - pair.row() - 1;
                ranks[pairIndex] = averageRank;
            }
            start = end;
        }
        return ranks;
    }

    private double pearson(double[] valuesA, double[] valuesB) {
        double meanA = mean(valuesA);
        double meanB = mean(valuesB);
        double covariance = 0.0;
        double varianceA = 0.0;
        double varianceB = 0.0;
        for (int index = 0; index < valuesA.length; index++) {
            double centeredA = valuesA[index] - meanA;
            double centeredB = valuesB[index] - meanB;
            covariance += centeredA * centeredB;
            varianceA += centeredA * centeredA;
            varianceB += centeredB * centeredB;
        }
        if (varianceA == 0.0 || varianceB == 0.0) {
            return 0.0;
        }
        return Math.max(-1.0, Math.min(1.0, covariance / Math.sqrt(varianceA * varianceB)));
    }

    private double mean(double[] values) {
        double total = 0.0;
        for (double value : values) {
            total += value;
        }
        return total / values.length;
    }

    private record PairScore(int row, int column, double score) {}
}
