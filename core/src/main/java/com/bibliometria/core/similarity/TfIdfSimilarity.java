package com.bibliometria.core.similarity;

import com.bibliometria.core.text.ProcessedDocument;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Similitud coseno entre vectores TF-IDF con IDF calculado sobre el corpus fijo de 20 documentos.
 *
 * <p>Mapeo fórmula-código: para cada término del documento, {@code peso(t,d) = tf(t,d) * ln(20 /
 * df(t))}; los dos vectores dispersos se comparan con el coseno. El valor crudo es el coseno y el
 * normalizado es el mismo valor acotado a {@code [0, 1]}.
 */
public final class TfIdfSimilarity implements SimilarityAlgorithm {

    private static final int CORPUS_SIZE = 20;

    private final Map<String, Integer> documentFrequencies;

    /**
     * Precalcula {@code df(t)} sobre el corpus global completo. La construcción exige los 20
     * documentos únicos; la selección de documentos que luego se compara no cambia el modelo IDF.
     * Términos que no aparecen en el corpus de referencia tienen peso cero.
     *
     * @param corpus documentos de referencia con sus conjuntos de tokens
     */
    public TfIdfSimilarity(List<ProcessedDocument> corpus) {
        Objects.requireNonNull(corpus, "corpus");
        if (corpus.size() != CORPUS_SIZE) {
            throw new IllegalArgumentException(
                    "The global reference corpus must contain exactly 20 documents.");
        }
        if (corpus.stream().map(ProcessedDocument::id).distinct().count() != CORPUS_SIZE) {
            throw new IllegalArgumentException(
                    "The global reference corpus must contain 20 unique document IDs.");
        }
        Map<String, Integer> frequencies = new HashMap<>();
        for (ProcessedDocument document : corpus) {
            Objects.requireNonNull(document, "corpus document");
            for (String token : document.conjunto()) {
                frequencies.merge(token, 1, Integer::sum);
            }
        }
        documentFrequencies = Map.copyOf(frequencies);
    }

    @Override
    public SimilarityResult calcular(ProcessedDocument documentA, ProcessedDocument documentB) {
        Objects.requireNonNull(documentA, "documentA");
        Objects.requireNonNull(documentB, "documentB");
        Map<String, Double> vectorA = toVector(documentA);
        Map<String, Double> vectorB = toVector(documentB);
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (double value : vectorA.values()) {
            normA += value * value;
        }
        for (double value : vectorB.values()) {
            normB += value * value;
        }
        for (Map.Entry<String, Double> entry : vectorA.entrySet()) {
            dotProduct += entry.getValue() * vectorB.getOrDefault(entry.getKey(), 0.0);
        }
        double cosine =
                normA == 0.0 || normB == 0.0
                        ? (normA == 0.0 && normB == 0.0 ? 1.0 : 0.0)
                        : dotProduct / Math.sqrt(normA * normB);
        double normalized = Math.max(0.0, Math.min(1.0, cosine));
        return new SimilarityResult(cosine, normalized);
    }

    private Map<String, Double> toVector(ProcessedDocument document) {
        Map<String, Double> vector = new HashMap<>();
        for (Map.Entry<String, Integer> entry : document.frecuencias().entrySet()) {
            int documentFrequency = documentFrequencies.getOrDefault(entry.getKey(), 0);
            if (documentFrequency > 0) {
                double idf = Math.log((double) CORPUS_SIZE / documentFrequency);
                vector.put(entry.getKey(), entry.getValue() * idf);
            }
        }
        return vector;
    }
}
