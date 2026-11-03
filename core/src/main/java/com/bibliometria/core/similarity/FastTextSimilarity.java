package com.bibliometria.core.similarity;

import com.bibliometria.core.text.ProcessedDocument;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Similitud coseno entre vectores documentales IA-1 promediados desde embeddings FastText.
 *
 * <p>El vector asociado al abstract de cada documento, {@code Tok(d)}, es el promedio de los
 * vectores FastText de sus tokens. Ese promedio se precalcula y queda expuesto por el proveedor de
 * embeddings como vector IA-1 de 300 dimensiones indexado por ID. El valor crudo es el coseno y el
 * normalizado es {@code (coseno + 1) / 2}.
 */
public final class FastTextSimilarity implements SimilarityAlgorithm {

    private final Map<String, double[]> documentVectors;

    /**
     * @param documentVectors vectores IA-1 de 300 dimensiones indexados por ID de documento
     */
    public FastTextSimilarity(Map<String, double[]> documentVectors) {
        this.documentVectors = copyAndValidate(documentVectors, 300, "IA-1");
    }

    @Override
    public SimilarityResult calcular(ProcessedDocument documentA, ProcessedDocument documentB) {
        Objects.requireNonNull(documentA, "documentA");
        Objects.requireNonNull(documentB, "documentB");
        double[] vectorA = requireVector(documentA.id());
        double[] vectorB = requireVector(documentB.id());
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int index = 0; index < vectorA.length; index++) {
            dot += vectorA[index] * vectorB[index];
            normA = Math.hypot(normA, vectorA[index]);
            normB = Math.hypot(normB, vectorB[index]);
        }
        double cosine =
                normA == 0.0 || normB == 0.0
                        ? (normA == 0.0 && normB == 0.0 ? 1.0 : 0.0)
                        : Math.max(-1.0, Math.min(1.0, dot / (normA * normB)));
        return new SimilarityResult(cosine, (cosine + 1.0) / 2.0);
    }

    private double[] requireVector(String id) {
        double[] vector = documentVectors.get(id);
        if (vector == null) {
            throw new IllegalArgumentException("No IA-1 vector found for document " + id);
        }
        return vector;
    }

    private static Map<String, double[]> copyAndValidate(
            Map<String, double[]> vectors, int dimensions, String model) {
        Objects.requireNonNull(vectors, "documentVectors");
        Map<String, double[]> copy = new HashMap<>();
        vectors.forEach(
                (id, vector) -> {
                    if (id == null
                            || id.isBlank()
                            || vector == null
                            || vector.length != dimensions) {
                        throw new IllegalArgumentException(
                                model
                                        + " vectors must have a non-empty ID and "
                                        + dimensions
                                        + " dimensions.");
                    }
                    double[] values = vector.clone();
                    for (double value : values) {
                        if (!Double.isFinite(value)) {
                            throw new IllegalArgumentException(
                                    model + " vectors must contain finite values.");
                        }
                    }
                    copy.put(id, values);
                });
        return Map.copyOf(copy);
    }
}
