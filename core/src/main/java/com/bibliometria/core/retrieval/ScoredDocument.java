package com.bibliometria.core.retrieval;

import com.bibliometria.core.text.ProcessedDocument;
import java.util.Objects;

/** Immutable search hit; component scores are normalized to {@code [0, 1]}. */
public record ScoredDocument(
        ProcessedDocument document, double score, double lexicalScore, double semanticScore) {

    public ScoredDocument {
        Objects.requireNonNull(document, "document");
        requireNormalized(score, "score");
        requireNormalized(lexicalScore, "lexicalScore");
        requireNormalized(semanticScore, "semanticScore");
    }

    public String id() {
        return document.id();
    }

    private static void requireNormalized(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1].");
        }
    }
}
