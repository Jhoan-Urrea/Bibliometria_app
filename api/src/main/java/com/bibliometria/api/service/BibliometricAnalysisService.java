package com.bibliometria.api.service;

import com.bibliometria.api.dto.ArticleDto;
import com.bibliometria.api.dto.ClusteringRequest;
import com.bibliometria.api.dto.ClusteringResponse;
import com.bibliometria.api.dto.LinkageDto;
import com.bibliometria.api.dto.SearchRequest;
import com.bibliometria.api.dto.SearchResponse;
import com.bibliometria.api.dto.SearchTraceResponse;
import com.bibliometria.api.dto.SimilarityRequest;
import com.bibliometria.api.dto.SimilarityResponse;
import com.bibliometria.api.mapper.ApiDtoMapper;
import com.bibliometria.core.clustering.AgglomerativeClustering;
import com.bibliometria.core.clustering.ClusterCut;
import com.bibliometria.core.clustering.DistanceMatrix;
import com.bibliometria.core.clustering.Merge;
import com.bibliometria.core.matching.CorpusArticleMatches;
import com.bibliometria.core.matching.CorpusSearchResult;
import com.bibliometria.core.matching.CorpusSearchService;
import com.bibliometria.core.matching.CorpusText;
import com.bibliometria.core.matching.JsonTraceRecorder;
import com.bibliometria.core.matching.MatchFragment;
import com.bibliometria.core.matching.TraceRecorder;
import com.bibliometria.core.retrieval.HybridSearchService;
import com.bibliometria.core.similarity.FastTextSimilarity;
import com.bibliometria.core.similarity.JaccardSimilarity;
import com.bibliometria.core.similarity.LcsSimilarity;
import com.bibliometria.core.similarity.LevenshteinSimilarity;
import com.bibliometria.core.similarity.SentenceBertSimilarity;
import com.bibliometria.core.similarity.SimilarityAlgorithm;
import com.bibliometria.core.similarity.TfIdfSimilarity;
import com.bibliometria.core.text.Article;
import com.bibliometria.core.text.ProcessedDocument;
import com.bibliometria.core.text.TextPreprocessor;
import com.bibliometria.embeddings.JsonEmbeddingProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class BibliometricAnalysisService {

    private final ApiDtoMapper mapper;
    private final List<Article> articles;
    private final Map<String, Article> articlesById;
    private final List<ProcessedDocument> processedDocuments;
    private final Map<String, ProcessedDocument> processedById;
    private final DistanceMatrix distanceMatrix;
    private final HybridSearchService searchService;
    private final Map<String, SimilarityAlgorithm> similarityAlgorithms;
    private final Map<LinkageDto, List<Merge>> mergesByLinkage;

    public BibliometricAnalysisService(
            ObjectMapper objectMapper,
            ApiDtoMapper mapper,
            @Value("${bibliometria.embeddings.directory:}") String embeddingsDirectory)
            throws IOException {
        this.mapper = mapper;
        this.articles = loadArticles(objectMapper);
        this.articlesById = indexArticles(articles);

        TextPreprocessor preprocessor = new TextPreprocessor();
        this.processedDocuments = articles.stream().map(preprocessor::process).toList();
        this.processedById = indexProcessedDocuments(processedDocuments);
        this.distanceMatrix = new DistanceMatrix(processedDocuments);
        this.searchService = new HybridSearchService(processedDocuments, preprocessor);
        this.similarityAlgorithms = createAlgorithms(processedDocuments, embeddingsDirectory);
        this.mergesByLinkage = createDendrograms();
    }

    public List<ArticleDto> listArticles() {
        return articles.stream().map(mapper::toDto).toList();
    }

    public SimilarityResponse calculateSimilarity(SimilarityRequest request) {
        Article articleA = requireArticle(request.articleAId());
        Article articleB = requireArticle(request.articleBId());
        ProcessedDocument documentA = processedById.get(articleA.id());
        ProcessedDocument documentB = processedById.get(articleB.id());
        String algorithmKey = mapper.toAlgorithmKey(request.algorithm());
        SimilarityAlgorithm algorithm = similarityAlgorithms.get(algorithmKey);
        if (algorithm == null) {
            throw new AnalysisNotPossibleException(
                    "El algoritmo semántico solicitado no está disponible en este servicio.");
        }
        return mapper.toDto(
                articleA.id(),
                articleB.id(),
                request.algorithm(),
                algorithm.calcular(documentA, documentB));
    }

    public ClusteringResponse cluster(ClusteringRequest request) {
        List<String> articleIds = distanceMatrix.documentIds();
        List<Merge> merges = mergesByLinkage.get(request.linkage());
        int[] labels = ClusterCut.cut(merges, request.clusterCount(), articleIds.size());
        return mapper.toDto(articleIds, request.linkage(), merges, labels);
    }

    public SearchResponse search(SearchRequest request) {
        return mapper.toDto(searchService.search(mapper.toDomain(request)), articlesById);
    }

    public SearchTraceResponse searchTrace(String pattern, String articleId) {
        List<ProcessedDocument> documents =
                articleId == null
                        ? processedDocuments
                        : List.of(requireProcessedDocument(articleId));
        TraceRecorder recorder =
                articleId == null ? TraceRecorder.sinTraza() : new JsonTraceRecorder();
        CorpusSearchResult result =
                new CorpusSearchService(recorder)
                        .buscar(
                                documents.stream()
                                        .map(
                                                document ->
                                                        new CorpusText(
                                                                document.id(),
                                                                document.textoNormalizado()))
                                        .toList(),
                                pattern);
        List<SearchTraceResponse.ArticleTrace> traces =
                result.articulos().stream()
                        .map(matches -> toArticleTrace(matches, recorder))
                        .toList();
        return new SearchTraceResponse(pattern, result.coinciden(), traces);
    }

    private SearchTraceResponse.ArticleTrace toArticleTrace(
            CorpusArticleMatches matches, TraceRecorder recorder) {
        List<SearchTraceResponse.TraceEvent> events =
                recorder instanceof JsonTraceRecorder jsonTraceRecorder
                        ? jsonTraceRecorder.eventos().stream()
                                .map(event -> toTraceEvent(matches.id(), event))
                                .toList()
                        : List.of();
        return new SearchTraceResponse.ArticleTrace(
                matches.id(),
                matches.posicionesPorAlgoritmo(),
                matches.fragmentos().stream().map(this::toMatchFragment).toList(),
                events);
    }

    private SearchTraceResponse.MatchFragment toMatchFragment(MatchFragment fragment) {
        return new SearchTraceResponse.MatchFragment(
                fragment.inicioTexto(),
                fragment.texto(),
                fragment.inicioResaltado(),
                fragment.finResaltado());
    }

    private SearchTraceResponse.TraceEvent toTraceEvent(
            String articleId, Map<String, Object> event) {
        Object rawData = event.get("datos");
        if (!(rawData instanceof Map<?, ?> data)) {
            throw new IllegalStateException("El núcleo devolvió datos de traza no válidos.");
        }

        Map<String, Object> values = new LinkedHashMap<>();
        data.forEach(
                (key, value) -> {
                    if (!(key instanceof String name)) {
                        throw new IllegalStateException("La traza contiene una clave no textual.");
                    }
                    values.put(name, value);
                });
        return new SearchTraceResponse.TraceEvent(
                articleId, (String) event.get("algoritmo"), (String) event.get("paso"), values);
    }

    private ProcessedDocument requireProcessedDocument(String articleId) {
        ProcessedDocument document = processedById.get(articleId);
        if (document == null) {
            throw new ArticleNotFoundException(articleId);
        }
        return document;
    }

    private Article requireArticle(String articleId) {
        Article article = articlesById.get(articleId);
        if (article == null) {
            throw new ArticleNotFoundException(articleId);
        }
        return article;
    }

    private List<Article> loadArticles(ObjectMapper objectMapper) throws IOException {
        ClassPathResource resource = new ClassPathResource("data/corpus.json");
        try (InputStream input = resource.getInputStream()) {
            List<CorpusRecord> records = objectMapper.readValue(input, new TypeReference<>() {});
            if (records == null || records.size() != 20) {
                throw new IOException("El corpus debe contener exactamente 20 artículos.");
            }
            List<Article> loaded = new ArrayList<>(records.size());
            Set<String> ids = new HashSet<>();
            for (CorpusRecord record : records) {
                if (record.id() == null || record.id().isBlank() || !ids.add(record.id())) {
                    throw new IOException(
                            "El corpus contiene un identificador inválido o repetido.");
                }
                loaded.add(
                        new Article(
                                record.id(),
                                record.title(),
                                record.authors(),
                                record.abstractText(),
                                record.verified()));
            }
            return List.copyOf(loaded);
        }
    }

    private Map<String, Article> indexArticles(List<Article> source) {
        Map<String, Article> indexed = new LinkedHashMap<>();
        source.forEach(article -> indexed.put(article.id(), article));
        return Map.copyOf(indexed);
    }

    private Map<String, ProcessedDocument> indexProcessedDocuments(List<ProcessedDocument> source) {
        Map<String, ProcessedDocument> indexed = new HashMap<>();
        source.forEach(document -> indexed.put(document.id(), document));
        return Map.copyOf(indexed);
    }

    private Map<String, SimilarityAlgorithm> createAlgorithms(
            List<ProcessedDocument> documents, String embeddingsDirectory) throws IOException {
        Map<String, SimilarityAlgorithm> algorithms = new HashMap<>();
        algorithms.put("levenshtein", new LevenshteinSimilarity());
        algorithms.put("lcs", new LcsSimilarity());
        algorithms.put("tfidf", new TfIdfSimilarity(documents));
        algorithms.put("jaccard", new JaccardSimilarity());

        if (embeddingsDirectory != null && !embeddingsDirectory.isBlank()) {
            Map<String, Map<String, double[]>> vectors =
                    new JsonEmbeddingProvider().load(Path.of(embeddingsDirectory));
            requireCompleteVectors("IA-1", vectors.get("IA-1"), documents);
            requireCompleteVectors("IA-2", vectors.get("IA-2"), documents);
            algorithms.put("fasttext", new FastTextSimilarity(vectors.get("IA-1")));
            algorithms.put("sentence-bert", new SentenceBertSimilarity(vectors.get("IA-2")));
        }
        return Map.copyOf(algorithms);
    }

    private Map<LinkageDto, List<Merge>> createDendrograms() {
        Map<LinkageDto, List<Merge>> dendrograms = new EnumMap<>(LinkageDto.class);
        for (LinkageDto linkage : LinkageDto.values()) {
            dendrograms.put(
                    linkage, AgglomerativeClustering.run(distanceMatrix, mapper.toDomain(linkage)));
        }
        return Map.copyOf(dendrograms);
    }

    private void requireCompleteVectors(
            String model, Map<String, double[]> vectors, List<ProcessedDocument> documents)
            throws IOException {
        Set<String> documentIds =
                documents.stream()
                        .map(ProcessedDocument::id)
                        .collect(java.util.stream.Collectors.toSet());
        if (vectors == null || !vectors.keySet().equals(documentIds)) {
            throw new IOException("Los embeddings de " + model + " no coinciden con el corpus.");
        }
    }

    public record CorpusRecord(
            String id,
            String filename,
            String sha256,
            String title,
            String authors,
            String abstractText,
            String language,
            Integer paginaAbstract,
            boolean verified,
            String verificadoPor,
            List<String> notas) {}
}
