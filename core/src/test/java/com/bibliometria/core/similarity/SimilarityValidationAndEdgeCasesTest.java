package com.bibliometria.core.similarity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bibliometria.core.text.ProcessedDocument;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SimilarityValidationAndEdgeCasesTest {

    private final LevenshteinSimilarity levenshtein = new LevenshteinSimilarity();
    private final LcsSimilarity lcs = new LcsSimilarity();
    private final JaccardSimilarity jaccard = new JaccardSimilarity();
    private final TfIdfSimilarity tfIdf = new TfIdfSimilarity(referenceCorpus());

    @Test
    void rejectsNonFiniteRawAndNormalizedScores() {
        for (double invalid :
                new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThatThrownBy(() -> new SimilarityResult(invalid, 0.5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cruda");
            assertThatThrownBy(() -> new SimilarityResult(0.0, invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("normalizada");
        }
    }

    @Test
    void rejectsNormalizedScoresOutsideFloatingPointTolerance() {
        assertThatThrownBy(() -> new SimilarityResult(0.0, -2e-12))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SimilarityResult(0.0, 1.0 + 2e-12))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clampsOnlyNormalizedScoresWithinFloatingPointTolerance() {
        assertThat(new SimilarityResult(0.0, -0.5e-12).normalizado()).isZero();
        assertThat(new SimilarityResult(0.0, 1.0 + 0.5e-12).normalizado()).isEqualTo(1.0);
        assertThat(new SimilarityResult(-1.0, 0.5).crudo()).isEqualTo(-1.0);
    }

    @Test
    void allTextMetricsRejectNullDocuments() {
        ProcessedDocument document = document("A01", "text", List.of("text"));
        List<SimilarityAlgorithm> algorithms = List.of(levenshtein, lcs, jaccard, tfIdf);
        for (SimilarityAlgorithm algorithm : algorithms) {
            assertThatThrownBy(() -> algorithm.calcular(null, document))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> algorithm.calcular(document, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    void allTextMetricsDefineEmptyDocumentBehavior() {
        ProcessedDocument emptyA = document("A01", "", List.of());
        ProcessedDocument emptyB = document("A02", "", List.of());
        for (SimilarityAlgorithm algorithm : List.of(levenshtein, lcs, jaccard, tfIdf)) {
            SimilarityResult result = algorithm.calcular(emptyA, emptyB);
            assertThat(result.normalizado())
                    .as("%s", algorithm.getClass().getSimpleName())
                    .isEqualTo(1.0);
        }

        ProcessedDocument nonEmpty = document("A03", "reference", List.of("reference"));
        assertThat(levenshtein.calcular(emptyA, nonEmpty).normalizado()).isZero();
        assertThat(lcs.calcular(emptyA, nonEmpty).normalizado()).isZero();
        assertThat(jaccard.calcular(emptyA, nonEmpty).normalizado()).isZero();
        assertThat(tfIdf.calcular(emptyA, nonEmpty).normalizado()).isZero();
    }

    @Test
    void levenshteinCountsUnicodeSupplementaryAndCombiningCodePoints() {
        SimilarityResult emojiToEmpty =
                levenshtein.calcular(
                        document("A01", "😀", List.of()), document("A02", "", List.of()));
        SimilarityResult sameMultibyteText =
                levenshtein.calcular(
                        document("A03", "café😀", List.of()), document("A04", "café😀", List.of()));
        SimilarityResult composedVersusDecomposedAccent =
                levenshtein.calcular(
                        document("A05", "é", List.of()), document("A06", "e\u0301", List.of()));

        assertThat(emojiToEmpty.crudo()).isEqualTo(1.0);
        assertThat(emojiToEmpty.normalizado()).isZero();
        assertThat(sameMultibyteText.crudo()).isZero();
        assertThat(sameMultibyteText.normalizado()).isEqualTo(1.0);
        assertThat(composedVersusDecomposedAccent.crudo()).isEqualTo(2.0);
        assertThat(composedVersusDecomposedAccent.normalizado()).isZero();
    }

    private static List<ProcessedDocument> referenceCorpus() {
        List<ProcessedDocument> documents = new ArrayList<>(20);
        documents.add(document("REF01", "reference text", List.of("reference", "text")));
        for (int index = 2; index <= 20; index++) {
            documents.add(document("REF%02d".formatted(index), "", List.of()));
        }
        return List.copyOf(documents);
    }

    private static ProcessedDocument document(String id, String text, List<String> tokens) {
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
