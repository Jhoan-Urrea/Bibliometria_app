package com.bibliometria.core.similarity;

import com.bibliometria.core.text.ProcessedDocument;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Calcula y cachea matrices simétricas de similitud para documentos seleccionados. */
public final class SimilarityMatrixService {

    private final ConcurrentHashMap<CacheKey, Double> cache = new ConcurrentHashMap<>();

    /**
     * Calcula una matriz cuadrada por algoritmo. Cada par se calcula una vez y se copia a ambas
     * posiciones; la diagonal se establece en uno por definición de similitud consigo mismo.
     *
     * @param documents documentos seleccionados con IDs únicos
     * @param algorithms algoritmos indexados por nombres estables
     * @return matriz {@code k × k} normalizada para cada algoritmo
     */
    public Map<String, double[][]> calculateMatrix(
            List<ProcessedDocument> documents, Map<String, SimilarityAlgorithm> algorithms) {
        Objects.requireNonNull(documents, "documents");
        Objects.requireNonNull(algorithms, "algorithms");
        List<ProcessedDocument> selected = List.copyOf(documents);
        if (selected.size() < 2) {
            throw new IllegalArgumentException("At least two documents must be selected.");
        }
        if (selected.stream().map(ProcessedDocument::id).distinct().count() != selected.size()) {
            throw new IllegalArgumentException("Document IDs must be unique.");
        }
        if (algorithms.isEmpty()
                || algorithms.entrySet().stream()
                        .anyMatch(
                                entry ->
                                        entry.getKey() == null
                                                || entry.getKey().isBlank()
                                                || entry.getValue() == null)) {
            throw new IllegalArgumentException(
                    "Algorithms must have non-empty names and implementations.");
        }

        Map<String, double[][]> matrices = new HashMap<>();
        for (Map.Entry<String, SimilarityAlgorithm> algorithm : algorithms.entrySet()) {
            int size = selected.size();
            double[][] matrix = new double[size][size];
            for (int row = 0; row < size; row++) {
                matrix[row][row] = 1.0;
                for (int column = row + 1; column < size; column++) {
                    ProcessedDocument documentA = selected.get(row);
                    ProcessedDocument documentB = selected.get(column);
                    ProcessedDocument first =
                            documentA.id().compareTo(documentB.id()) <= 0 ? documentA : documentB;
                    ProcessedDocument second = first == documentA ? documentB : documentA;
                    CacheKey key =
                            new CacheKey(
                                    algorithm.getKey(),
                                    algorithm.getValue(),
                                    first.id(),
                                    second.id(),
                                    first,
                                    second);
                    double similarity =
                            cache.computeIfAbsent(
                                    key,
                                    ignored ->
                                            algorithm
                                                    .getValue()
                                                    .calcular(first, second)
                                                    .normalizado());
                    if (!Double.isFinite(similarity) || similarity < 0.0 || similarity > 1.0) {
                        throw new IllegalArgumentException(
                                "Algorithm "
                                        + algorithm.getKey()
                                        + " returned a value outside [0, 1].");
                    }
                    matrix[row][column] = similarity;
                    matrix[column][row] = similarity;
                }
            }
            matrices.put(algorithm.getKey(), matrix);
        }
        return Map.copyOf(matrices);
    }

    /** @return número de resultados actualmente guardados en la caché */
    public int cachedPairCount() {
        return cache.size();
    }

    private record CacheKey(
            String algorithmName,
            SimilarityAlgorithm algorithm,
            String firstDocumentId,
            String secondDocumentId,
            ProcessedDocument firstDocument,
            ProcessedDocument secondDocument) {}
}
