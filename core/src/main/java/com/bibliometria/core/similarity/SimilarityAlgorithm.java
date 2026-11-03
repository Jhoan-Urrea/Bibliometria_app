package com.bibliometria.core.similarity;

import com.bibliometria.core.text.ProcessedDocument;

/** Contrato para calcular la similitud entre dos vectores. */
public interface SimilarityAlgorithm {

    /**
     * Calcula la similitud entre vectores del mismo espacio.
     *
     * @param documentA primer documento procesado
     * @param documentB segundo documento procesado
     * @return similitud cruda y su valor normalizado al rango {@code [0, 1]}
     */
    SimilarityResult calcular(ProcessedDocument documentA, ProcessedDocument documentB);
}
