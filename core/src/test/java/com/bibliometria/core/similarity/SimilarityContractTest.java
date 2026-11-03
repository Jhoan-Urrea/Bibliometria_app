package com.bibliometria.core.similarity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.bibliometria.core.text.ProcessedDocument;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pruebas compartidas para validar las propiedades requeridas de una similitud. */
public abstract class SimilarityContractTest {

    private SimilarityAlgorithm algorithm;

    protected abstract SimilarityAlgorithm crearAlgoritmo();

    @BeforeEach
    void setUp() {
        algorithm = crearAlgoritmo();
    }

    @Test
    void similarityWithItselfIsOne() {
        ProcessedDocument document = document("a b c", List.of("a", "b", "c"));

        assertThat(algorithm.calcular(document, document).normalizado())
                .isCloseTo(1.0, within(1e-9));
    }

    @Test
    void similarityIsSymmetric() {
        ProcessedDocument documentA = document("a b c", List.of("a", "b", "c"));
        ProcessedDocument documentB = document("c b a", List.of("c", "b", "a"));

        double forward = algorithm.calcular(documentA, documentB).normalizado();
        double reverse = algorithm.calcular(documentB, documentA).normalizado();

        assertThat(forward).isCloseTo(reverse, within(1e-9));
    }

    @Test
    void normalizedSimilarityIsWithinZeroAndOne() {
        ProcessedDocument documentA = document("a b c", List.of("a", "b", "c"));
        ProcessedDocument documentB = document("x y", List.of("x", "y"));

        double normalized = algorithm.calcular(documentA, documentB).normalizado();

        assertThat(normalized).isBetween(0.0, 1.0);
    }

    protected static ProcessedDocument document(String text, List<String> tokens) {
        return new ProcessedDocument(
                documentId(text),
                text,
                tokens,
                Set.copyOf(tokens),
                tokens.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        token -> token, token -> 1, Integer::sum)),
                String.join(" ", tokens));
    }

    protected static String documentId(String text) {
        return "contract-" + Integer.toHexString(text.hashCode());
    }
}
