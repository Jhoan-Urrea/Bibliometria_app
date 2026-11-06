package com.bibliometria.core.similarity;

import com.bibliometria.core.text.ProcessedDocument;
import java.util.Objects;
import java.util.Set;

/**
 * Similitud de Jaccard entre los conjuntos de tokens de dos documentos.
 *
 * <p>Mapeo fórmula-código: el valor crudo y normalizado son {@code |A ∩ B| / |A ∪ B|}; si ambos
 * conjuntos están vacíos, se define el resultado como uno.
 */
public final class JaccardSimilarity implements SimilarityAlgorithm {

    /**
     * Calcula {@code |A ∩ B| / |A ∪ B|} sobre los conjuntos de tokens; si ambos están vacíos,
     * define la similitud como {@code 1}.
     *
     * @param documentA primer documento
     * @param documentB segundo documento
     * @return similitud cruda y normalizada, ambas en {@code [0, 1]}
     */
    @Override
    public SimilarityResult calcular(ProcessedDocument documentA, ProcessedDocument documentB) {
        Objects.requireNonNull(documentA, "documentA");
        Objects.requireNonNull(documentB, "documentB");
        Set<String> setA = documentA.conjunto();
        Set<String> setB = documentB.conjunto();
        long intersectionSize = setA.stream().filter(setB::contains).count();
        long unionSize = (long) setA.size() + setB.size() - intersectionSize;
        double similarity = unionSize == 0 ? 1.0 : (double) intersectionSize / unionSize;
        return new SimilarityResult(similarity, similarity);
    }
}
