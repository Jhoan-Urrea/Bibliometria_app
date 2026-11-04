package com.bibliometria.core.clustering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bibliometria.core.text.ProcessedDocument;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DistanceMatrixTest {

    @Test
    void buildsSymmetricBoundedMatrixWithStrictZeroDiagonal() {
        DistanceMatrix matrix = new DistanceMatrix(corpus());

        assertThat(matrix.size()).isEqualTo(20);
        assertThat(matrix.documentIds()).containsExactlyElementsOf(expectedIds());
        for (int row = 0; row < matrix.size(); row++) {
            assertThat(matrix.distance(row, row)).isEqualTo(0.0);
            for (int column = 0; column < matrix.size(); column++) {
                assertThat(matrix.distance(row, column)).isBetween(0.0, 1.0);
                assertThat(matrix.distance(row, column)).isEqualTo(matrix.distance(column, row));
            }
        }
        matrix.validar();
    }

    @Test
    void calculatesDistanceAsOneMinusTheExistingTfIdfCosine() {
        DistanceMatrix matrix = new DistanceMatrix(corpus());

        assertThat(matrix.distance("A01", "A02")).isEqualTo(0.0);
        assertThat(matrix.distance("A01", "A03")).isEqualTo(1.0);
        assertThat(matrix.distance("A02", "A03")).isEqualTo(1.0);
    }

    @Test
    void producesIdenticalCanonicalMatrixRegardlessOfInputOrder() {
        List<ProcessedDocument> documents = corpus();
        List<ProcessedDocument> reversed = new ArrayList<>(documents);
        java.util.Collections.reverse(reversed);

        DistanceMatrix first = new DistanceMatrix(documents);
        DistanceMatrix second = new DistanceMatrix(reversed);

        assertThat(second.documentIds()).isEqualTo(first.documentIds());
        assertThat(second.toArray()).isDeepEqualTo(first.toArray());
    }

    @Test
    void returnedArrayCannotMutateTheMatrix() {
        DistanceMatrix matrix = new DistanceMatrix(corpus());
        double original = matrix.distance("A01", "A03");
        double[][] copy = matrix.toArray();
        copy[0][2] = 0.25;
        copy[2][0] = 0.25;

        assertThat(matrix.distance("A01", "A03")).isEqualTo(original);
        assertThat(matrix.distance("A03", "A01")).isEqualTo(original);
    }

    @Test
    void rejectsIncorrectCorpusAndUnknownIds() {
        assertThatThrownBy(() -> new DistanceMatrix(List.of(document("A01", "term"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly 20 documents");

        DistanceMatrix matrix = new DistanceMatrix(corpus());
        assertThatThrownBy(() -> matrix.distance("A99", "A01"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> matrix.distance(null, "A01"))
                .isInstanceOf(NullPointerException.class);
    }

    private static List<String> expectedIds() {
        List<String> ids = new ArrayList<>(20);
        for (int index = 1; index <= 20; index++) {
            ids.add("A%02d".formatted(index));
        }
        return List.copyOf(ids);
    }

    private static List<ProcessedDocument> corpus() {
        List<ProcessedDocument> documents = new ArrayList<>(20);
        documents.add(document("A01", "alpha beta"));
        documents.add(document("A02", "alpha beta"));
        documents.add(document("A03", "gamma delta"));
        for (int index = 4; index <= 20; index++) {
            documents.add(document("A%02d".formatted(index), ""));
        }
        return List.copyOf(documents);
    }

    private static ProcessedDocument document(String id, String text) {
        List<String> tokens = text.isBlank() ? List.of() : List.of(text.split("\\s+"));
        Map<String, Integer> frequencies = new LinkedHashMap<>();
        tokens.forEach(token -> frequencies.merge(token, 1, Integer::sum));
        return new ProcessedDocument(
                id,
                text,
                tokens,
                Set.copyOf(frequencies.keySet()),
                frequencies,
                String.join(" ", tokens));
    }
}
