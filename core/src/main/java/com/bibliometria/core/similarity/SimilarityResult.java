package com.bibliometria.core.similarity;

/** Resultado crudo y normalizado de un cálculo de similitud. */
public record SimilarityResult(double crudo, double normalizado) {

    private static final double NORMALIZED_TOLERANCE = 1e-12;

    public SimilarityResult {
        if (!Double.isFinite(crudo)) {
            throw new IllegalArgumentException("La puntuación cruda debe ser finita.");
        }
        if (!Double.isFinite(normalizado)) {
            throw new IllegalArgumentException("La puntuación normalizada debe ser finita.");
        }
        if (normalizado < -NORMALIZED_TOLERANCE || normalizado > 1.0 + NORMALIZED_TOLERANCE) {
            throw new IllegalArgumentException(
                    "La puntuación normalizada debe estar en el rango [0, 1].");
        }
        normalizado = Math.max(0.0, Math.min(1.0, normalizado));
    }
}
