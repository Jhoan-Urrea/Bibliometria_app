package com.bibliometria.core.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.bibliometria.core.text.Article;
import com.bibliometria.core.text.ProcessedDocument;
import com.bibliometria.core.text.TextPreprocessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class SearchCorpusIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void bm25RetrievesExpectedDocumentsFromTheTwentyDocumentCorpus() throws IOException {
        List<ProcessedDocument> corpus = loadCorpus();
        HybridSearchService service = new HybridSearchService(corpus, new TextPreprocessor());

        SearchResult literacy = service.search(new SearchQuery("literacy", 1, 5));
        SearchResult unusedTerm = service.search(new SearchQuery("qzxvnonexistent", 1, 5));

        assertThat(corpus).hasSize(20);
        assertThat(literacy.totalHits()).isGreaterThan(0);
        assertThat(literacy.results().getFirst().id()).isEqualTo("A01");
        assertThat(literacy.results().getFirst().score()).isEqualTo(1.0);
        assertThat(unusedTerm.results()).isEmpty();
        assertThat(unusedTerm.totalHits()).isZero();
    }

    @Test
    void reportsLatencyForRepeatedTwentyDocumentRetrieval() throws IOException {
        HybridSearchService service = new HybridSearchService(loadCorpus(), new TextPreprocessor());
        SearchQuery query = new SearchQuery("artificial intelligence", 1, 10);
        for (int iteration = 0; iteration < 100; iteration++) {
            service.search(query);
        }

        long[] samples = new long[500];
        for (int iteration = 0; iteration < samples.length; iteration++) {
            long start = System.nanoTime();
            SearchResult result = service.search(query);
            samples[iteration] = System.nanoTime() - start;
            assertThat(result.results()).hasSizeLessThanOrEqualTo(10);
        }
        java.util.Arrays.sort(samples);
        double averageMicros = java.util.Arrays.stream(samples).average().orElseThrow() / 1_000.0;
        double p95Micros = samples[(int) Math.ceil(samples.length * 0.95) - 1] / 1_000.0;
        System.out.printf(
                Locale.ROOT,
                "F2 BM25 latency (20 docs, 500 measured queries): avg=%.2f us, p95=%.2f us%n",
                averageMicros,
                p95Micros);
        assertThat(p95Micros).isPositive();
    }

    private List<ProcessedDocument> loadCorpus() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/golden/corpus.json")) {
            if (input == null) {
                throw new IOException("Missing frozen corpus integration fixture.");
            }
            JsonNode records = objectMapper.readTree(input);
            List<ProcessedDocument> documents = new ArrayList<>(records.size());
            TextPreprocessor preprocessor = new TextPreprocessor();
            for (JsonNode record : records) {
                documents.add(
                        preprocessor.process(
                                new Article(
                                        record.path("id").asText(),
                                        record.path("title").asText(),
                                        record.path("authors").asText(),
                                        record.path("abstractText").asText(),
                                        record.path("verified").asBoolean())));
            }
            documents.sort(Comparator.comparing(ProcessedDocument::id));
            return List.copyOf(documents);
        }
    }
}
