package com.bibliometria.core.retrieval;

import com.bibliometria.core.text.Article;
import com.bibliometria.core.text.ProcessedDocument;
import com.bibliometria.core.text.TextPreprocessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** BM25 lexical retrieval and deterministic Reciprocal Rank Fusion with IA-2 vectors. */
public final class HybridSearchService {

    private static final int IA2_DIMENSIONS = 384;
    private static final int DEFAULT_RRF_CONSTANT = 60;
    private static final Comparator<RankedHit> HIT_ORDER =
            Comparator.comparingDouble(RankedHit::score).reversed().thenComparing(RankedHit::id);

    private final List<ProcessedDocument> documents;
    private final Map<String, ProcessedDocument> documentsById;
    private final TextPreprocessor preprocessor;
    private final Map<String, Integer> documentFrequencies;
    private final Map<String, List<ProcessedDocument>> postingsByTerm;
    private final double averageDocumentLength;
    private final double k1;
    private final double b;
    private final int rrfConstant;

    public HybridSearchService(List<ProcessedDocument> documents, TextPreprocessor preprocessor) {
        this(documents, preprocessor, 1.2, 0.75, DEFAULT_RRF_CONSTANT);
    }

    public HybridSearchService(
            List<ProcessedDocument> documents,
            TextPreprocessor preprocessor,
            double k1,
            double b,
            int rrfConstant) {
        Objects.requireNonNull(documents, "documents");
        this.preprocessor = Objects.requireNonNull(preprocessor, "preprocessor");
        if (documents.isEmpty()) {
            throw new IllegalArgumentException("The search corpus must not be empty.");
        }
        if (!Double.isFinite(k1) || k1 <= 0.0) {
            throw new IllegalArgumentException("BM25 k1 must be finite and positive.");
        }
        if (!Double.isFinite(b) || b < 0.0 || b > 1.0) {
            throw new IllegalArgumentException("BM25 b must be in [0, 1].");
        }
        if (rrfConstant < 0) {
            throw new IllegalArgumentException("RRF constant must not be negative.");
        }

        List<ProcessedDocument> copy = List.copyOf(documents);
        Set<String> ids = new HashSet<>();
        Map<String, Integer> frequencies = new HashMap<>();
        Map<String, List<ProcessedDocument>> postings = new HashMap<>();
        long totalLength = 0;
        for (ProcessedDocument document : copy) {
            Objects.requireNonNull(document, "corpus document");
            if (!ids.add(document.id())) {
                throw new IllegalArgumentException("Duplicate document ID: " + document.id());
            }
            totalLength += document.tokens().size();
            for (String token : document.conjunto()) {
                frequencies.merge(token, 1, Integer::sum);
                postings.computeIfAbsent(token, ignored -> new ArrayList<>()).add(document);
            }
        }
        this.documents = copy;
        Map<String, ProcessedDocument> byId = new HashMap<>();
        copy.forEach(document -> byId.put(document.id(), document));
        this.documentsById = Map.copyOf(byId);
        this.documentFrequencies = Map.copyOf(frequencies);
        Map<String, List<ProcessedDocument>> immutablePostings = new HashMap<>();
        postings.forEach((token, posting) -> immutablePostings.put(token, List.copyOf(posting)));
        this.postingsByTerm = Map.copyOf(immutablePostings);
        this.averageDocumentLength = (double) totalLength / copy.size();
        this.k1 = k1;
        this.b = b;
        this.rrfConstant = rrfConstant;
    }

    /** Searches by BM25 and returns only documents with at least one query-term match. */
    public SearchResult search(SearchQuery query) {
        Objects.requireNonNull(query, "query");
        List<String> queryTokens = preprocessor.process(queryArticle(query.text())).tokens();
        if (queryTokens.isEmpty()) {
            return pageScored(query, List.of());
        }

        return pageRanked(query, lexicalRanking(queryTokens));
    }

    /**
     * Fuses BM25 and IA-2 cosine rankings. Embeddings must cover every indexed document and use
     * normalized 384-dimensional vectors as produced by Sentence-Transformers.
     */
    public SearchResult searchHybrid(
            SearchQuery query, Map<String, double[]> documentVectors, double[] queryVector) {
        Objects.requireNonNull(query, "query");
        Map<String, double[]> vectors = validateVectors(documentVectors, queryVector);

        List<RankedHit> lexical = lexicalRanking(query);
        List<RankedHit> semantic = semanticRanking(vectors, queryVector);
        Map<String, Double> lexicalScores = scoreMap(lexical);
        Map<String, Double> semanticScores = scoreMap(semantic);
        Map<String, Double> fused = new HashMap<>();
        addRrfRanks(fused, lexical);
        addRrfRanks(fused, semantic);

        double maximum = fused.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        List<RankedHit> ranked =
                fused.entrySet().stream()
                        .map(entry -> new RankedHit(entry.getKey(), entry.getValue() / maximum))
                        .sorted(HIT_ORDER)
                        .toList();
        List<ScoredDocument> scored =
                ranked.stream()
                        .map(
                                hit ->
                                        new ScoredDocument(
                                                documentsById.get(hit.id()),
                                                hit.score(),
                                                lexicalScores.getOrDefault(hit.id(), 0.0),
                                                semanticScores.getOrDefault(hit.id(), 0.0)))
                        .toList();
        return pageScored(query, scored);
    }

    private List<RankedHit> lexicalRanking(SearchQuery query) {
        List<String> queryTokens = preprocessor.process(queryArticle(query.text())).tokens();
        return lexicalRanking(queryTokens);
    }

    private List<RankedHit> lexicalRanking(List<String> queryTokens) {
        Map<String, Double> scores = new HashMap<>();
        for (String token : queryTokens) {
            List<ProcessedDocument> postings = postingsByTerm.get(token);
            if (postings == null) {
                continue;
            }
            int documentFrequency = documentFrequencies.get(token);
            double inverseDocumentFrequency =
                    Math.log(
                            1.0
                                    + (documents.size() - documentFrequency + 0.5)
                                            / (documentFrequency + 0.5));
            for (ProcessedDocument document : postings) {
                int termFrequency = document.frecuencias().get(token);
                int documentLength = document.tokens().size();
                double lengthNormalization =
                        averageDocumentLength == 0.0
                                ? 0.0
                                : k1 * (1.0 - b + b * documentLength / averageDocumentLength);
                double contribution =
                        inverseDocumentFrequency
                                * termFrequency
                                * (k1 + 1.0)
                                / (termFrequency + lengthNormalization);
                scores.merge(document.id(), contribution, Double::sum);
            }
        }
        double maximum =
                scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        return scores.entrySet().stream()
                .map(entry -> new RankedHit(entry.getKey(), entry.getValue() / maximum))
                .sorted(HIT_ORDER)
                .toList();
    }

    private List<RankedHit> semanticRanking(Map<String, double[]> vectors, double[] queryVector) {
        List<RankedHit> ranking = new ArrayList<>(documents.size());
        for (ProcessedDocument document : documents) {
            double[] vector = vectors.get(document.id());
            double cosine = dotProduct(queryVector, vector);
            ranking.add(
                    new RankedHit(
                            document.id(), (Math.max(-1.0, Math.min(1.0, cosine)) + 1.0) / 2.0));
        }
        ranking.sort(HIT_ORDER);
        return List.copyOf(ranking);
    }

    private Map<String, double[]> validateVectors(
            Map<String, double[]> suppliedVectors, double[] suppliedQueryVector) {
        Objects.requireNonNull(suppliedVectors, "documentVectors");
        validateUnitVector(suppliedQueryVector, "query vector");
        if (suppliedVectors.size() != documents.size()) {
            throw new IllegalArgumentException(
                    "An IA-2 vector is required for every corpus document.");
        }
        Map<String, double[]> copy = new HashMap<>();
        for (ProcessedDocument document : documents) {
            double[] vector = suppliedVectors.get(document.id());
            validateUnitVector(vector, "IA-2 vector for " + document.id());
            copy.put(document.id(), vector.clone());
        }
        if (copy.size() != suppliedVectors.size()) {
            throw new IllegalArgumentException("Embedding IDs must match the indexed corpus.");
        }
        return Map.copyOf(copy);
    }

    private static void validateUnitVector(double[] vector, String name) {
        if (vector == null || vector.length != IA2_DIMENSIONS) {
            throw new IllegalArgumentException(name + " must have 384 dimensions.");
        }
        double norm = 0.0;
        for (double value : vector) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(name + " must contain finite values.");
            }
            norm = Math.hypot(norm, value);
        }
        if (Math.abs(norm - 1.0) > 1e-6) {
            throw new IllegalArgumentException(name + " must have unit L2 norm.");
        }
    }

    private static double dotProduct(double[] vectorA, double[] vectorB) {
        double dot = 0.0;
        for (int index = 0; index < vectorA.length; index++) {
            dot += vectorA[index] * vectorB[index];
        }
        return dot;
    }

    private void addRrfRanks(Map<String, Double> scores, List<RankedHit> ranking) {
        for (int index = 0; index < ranking.size(); index++) {
            scores.merge(ranking.get(index).id(), 1.0 / (rrfConstant + index + 1.0), Double::sum);
        }
    }

    private static Map<String, Double> scoreMap(List<RankedHit> ranking) {
        Map<String, Double> scores = new HashMap<>();
        ranking.forEach(hit -> scores.put(hit.id(), hit.score()));
        return scores;
    }

    private SearchResult pageRanked(SearchQuery query, List<RankedHit> ranking) {
        List<ScoredDocument> scored =
                ranking.stream()
                        .map(
                                hit ->
                                        new ScoredDocument(
                                                documentsById.get(hit.id()),
                                                hit.score(),
                                                hit.score(),
                                                0.0))
                        .toList();
        return pageScored(query, scored);
    }

    private SearchResult pageScored(SearchQuery query, List<ScoredDocument> ranking) {
        int totalHits = ranking.size();
        int totalPages = (int) Math.ceil((double) totalHits / query.pageSize());
        long startLong = (long) (query.page() - 1) * query.pageSize();
        int start = (int) Math.min(startLong, totalHits);
        int end = Math.min(start + query.pageSize(), totalHits);
        return new SearchResult(
                query.text(),
                ranking.subList(start, end),
                totalHits,
                query.page(),
                query.pageSize(),
                totalPages);
    }

    private static Article queryArticle(String query) {
        return new Article("search-query", "", "", query, false);
    }

    private record RankedHit(String id, double score) {}
}
