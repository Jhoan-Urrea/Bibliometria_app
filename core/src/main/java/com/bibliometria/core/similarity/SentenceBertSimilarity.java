package com.bibliometria.core.similarity;

import com.bibliometria.core.text.ProcessedDocument;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Cosine similarity for normalized 384-dimensional IA-2 embeddings. */
public final class SentenceBertSimilarity implements SimilarityAlgorithm {

    private final Map<String, double[]> documentVectors;

    /**
     * @param documentVectors embeddings IA-2 de 384 dimensiones indexados por ID de documento
     */
    public SentenceBertSimilarity(Map<String, double[]> documentVectors) {
        this.documentVectors = copyAndValidate(documentVectors);
    }

    @Override
    public SimilarityResult calcular(ProcessedDocument documentA, ProcessedDocument documentB) {
        Objects.requireNonNull(documentA, "documentA");
        Objects.requireNonNull(documentB, "documentB");
        double[] vectorA = requireVector(documentA.id());
        double[] vectorB = requireVector(documentB.id());
        double cosine = 0.0;
        for (int index = 0; index < vectorA.length; index++) {
            cosine += vectorA[index] * vectorB[index];
        }
        cosine = Math.max(-1.0, Math.min(1.0, cosine));
        return new SimilarityResult(cosine, (cosine + 1.0) / 2.0);
    }

    private double[] requireVector(String id) {
        double[] vector = documentVectors.get(id);
        if (vector == null) {
            throw new IllegalArgumentException("No IA-2 embedding found for document " + id);
        }
        return vector;
    }

    private static Map<String, double[]> copyAndValidate(Map<String, double[]> vectors) {
        Objects.requireNonNull(vectors, "documentVectors");
        Map<String, double[]> copy = new HashMap<>();
        vectors.forEach(
                (id, vector) -> {
                    if (id == null || id.isBlank() || vector == null || vector.length != 384) {
                        throw new IllegalArgumentException(
                                "IA-2 embeddings must have a non-empty ID and 384 dimensions.");
                    }
                    double[] values = vector.clone();
                    double norm = 0.0;
                    for (double value : values) {
                        if (!Double.isFinite(value)) {
                            throw new IllegalArgumentException(
                                    "IA-2 embeddings must contain finite values.");
                        }
                        norm = Math.hypot(norm, value);
                    }
                    if (Math.abs(norm - 1.0) > 1e-6) {
                        throw new IllegalArgumentException(
                                "IA-2 embeddings must have unit L2 norm.");
                    }
                    copy.put(id, values);
                });
        return Map.copyOf(copy);
    }
}
