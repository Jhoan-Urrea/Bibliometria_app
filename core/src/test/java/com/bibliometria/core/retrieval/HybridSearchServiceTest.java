package com.bibliometria.core.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.bibliometria.core.text.Article;
import com.bibliometria.core.text.ProcessedDocument;
import com.bibliometria.core.text.TextPreprocessor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HybridSearchServiceTest {

    private final TextPreprocessor preprocessor = new TextPreprocessor();

    @Test
    void bm25ReturnsNormalizedDeterministicResultsAndPagesThem() {
        HybridSearchService service =
                new HybridSearchService(
                        List.of(
                                document("A02", "quantum retrieval"),
                                document("A01", "quantum retrieval"),
                                document("A03", "quantum search search")),
                        preprocessor);

        SearchResult firstPage = service.search(new SearchQuery("quantum", 1, 2));
        SearchResult secondPage = service.search(new SearchQuery("quantum", 2, 2));

        assertThat(firstPage.results())
                .extracting(ScoredDocument::id)
                .containsExactly("A01", "A02");
        assertThat(firstPage.results().getFirst().document().cadenaCanonica()).contains("quantum");
        assertThat(firstPage.results())
                .allSatisfy(hit -> assertThat(hit.score()).isBetween(0.0, 1.0));
        assertThat(firstPage.results().getFirst().score())
                .isEqualTo(firstPage.results().getLast().score());
        assertThat(firstPage.totalHits()).isEqualTo(3);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(secondPage.results()).extracting(ScoredDocument::id).containsExactly("A03");
    }

    @Test
    void hybridSearchFusesRankingsAndUsesDocumentIdForExactTies() {
        List<ProcessedDocument> documents =
                List.of(document("A02", "quantum retrieval"), document("A01", "quantum retrieval"));
        HybridSearchService service = new HybridSearchService(documents, preprocessor);
        Map<String, double[]> vectors = new LinkedHashMap<>();
        vectors.put("A02", unitVector(0));
        vectors.put("A01", unitVector(0));

        SearchResult result =
                service.searchHybrid(new SearchQuery("quantum", 1, 10), vectors, unitVector(0));

        assertThat(result.results()).extracting(ScoredDocument::id).containsExactly("A01", "A02");
        assertThat(result.results().getFirst().score()).isEqualTo(1.0);
        assertThat(result.results().getLast().score()).isCloseTo(61.0 / 62.0, within(1e-12));
        assertThat(result.results())
                .allSatisfy(
                        hit -> {
                            assertThat(hit.lexicalScore()).isEqualTo(1.0);
                            assertThat(hit.semanticScore()).isEqualTo(1.0);
                        });
    }

    @Test
    void rejectsIncompleteOrInvalidSemanticIndexes() {
        HybridSearchService service =
                new HybridSearchService(List.of(document("A01", "quantum")), preprocessor);

        assertThatThrownBy(
                        () ->
                                service.searchHybrid(
                                        new SearchQuery("quantum"), Map.of(), unitVector(0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("every corpus document");
        assertThatThrownBy(
                        () ->
                                service.searchHybrid(
                                        new SearchQuery("quantum"),
                                        Map.of("A01", unitVector(0)),
                                        new double[384]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unit L2 norm");
    }

    @Test
    void validatesQueryPaginationAndBm25Configuration() {
        assertThatThrownBy(() -> new SearchQuery(" ", 1, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SearchQuery("term", 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SearchQuery("term", 1, 101))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new HybridSearchService(
                                        List.of(document("A01", "term")),
                                        preprocessor,
                                        1.2,
                                        2.0,
                                        60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("b must be in [0, 1]");
    }

    private ProcessedDocument document(String id, String text) {
        return preprocessor.process(new Article(id, "", "", text, false));
    }

    private static double[] unitVector(int nonZeroIndex) {
        double[] vector = new double[384];
        vector[nonZeroIndex] = 1.0;
        return vector;
    }
}
