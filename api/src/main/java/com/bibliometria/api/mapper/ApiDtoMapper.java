package com.bibliometria.api.mapper;

import com.bibliometria.api.dto.ArticleDto;
import com.bibliometria.api.dto.ClusteringResponse;
import com.bibliometria.api.dto.LinkageDto;
import com.bibliometria.api.dto.SearchRequest;
import com.bibliometria.api.dto.SearchResponse;
import com.bibliometria.api.dto.SimilarityAlgorithmDto;
import com.bibliometria.api.dto.SimilarityResponse;
import com.bibliometria.core.clustering.Linkage;
import com.bibliometria.core.clustering.Merge;
import com.bibliometria.core.retrieval.SearchQuery;
import com.bibliometria.core.retrieval.SearchResult;
import com.bibliometria.core.similarity.SimilarityResult;
import com.bibliometria.core.text.Article;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ApiDtoMapper {

    public ArticleDto toDto(Article article) {
        return new ArticleDto(
                article.id(),
                article.title(),
                article.authors(),
                article.abstractText(),
                article.verified());
    }

    public SimilarityResponse toDto(
            String articleAId,
            String articleBId,
            SimilarityAlgorithmDto algorithm,
            SimilarityResult result) {
        return new SimilarityResponse(
                articleAId, articleBId, algorithm, result.crudo(), result.normalizado());
    }

    public SearchQuery toDomain(SearchRequest request) {
        return new SearchQuery(request.query(), request.page(), request.pageSize());
    }

    public SearchResponse toDto(SearchResult result, Map<String, Article> articlesById) {
        List<SearchResponse.SearchHitDto> hits =
                result.results().stream()
                        .map(
                                hit ->
                                        new SearchResponse.SearchHitDto(
                                                toDto(articlesById.get(hit.id())),
                                                hit.score(),
                                                hit.lexicalScore(),
                                                hit.semanticScore()))
                        .toList();
        return new SearchResponse(
                result.query(),
                hits,
                result.totalHits(),
                result.page(),
                result.pageSize(),
                result.totalPages());
    }

    public ClusteringResponse toDto(
            List<String> articleIds, LinkageDto linkage, List<Merge> merges, int[] labels) {
        List<List<String>> articleIdsByLabel = new ArrayList<>();
        for (int label = 0; label < maxLabel(labels) + 1; label++) {
            articleIdsByLabel.add(new ArrayList<>());
        }
        for (int index = 0; index < labels.length; index++) {
            articleIdsByLabel.get(labels[index]).add(articleIds.get(index));
        }
        List<ClusteringResponse.ClusterDto> clusters = new ArrayList<>();
        for (int label = 0; label < articleIdsByLabel.size(); label++) {
            clusters.add(new ClusteringResponse.ClusterDto(label, articleIdsByLabel.get(label)));
        }
        List<ClusteringResponse.MergeDto> mergeDtos =
                merges.stream()
                        .map(
                                merge ->
                                        new ClusteringResponse.MergeDto(
                                                merge.leftChild(),
                                                merge.rightChild(),
                                                merge.height(),
                                                merge.size()))
                        .toList();
        return new ClusteringResponse(articleIds, linkage, clusters, mergeDtos);
    }

    public Linkage toDomain(LinkageDto linkage) {
        return switch (linkage) {
            case SINGLE -> new com.bibliometria.core.clustering.SingleLinkage();
            case COMPLETE -> new com.bibliometria.core.clustering.CompleteLinkage();
            case AVERAGE -> new com.bibliometria.core.clustering.AverageLinkage();
        };
    }

    public String toAlgorithmKey(SimilarityAlgorithmDto algorithm) {
        return switch (algorithm) {
            case LEVENSHTEIN -> "levenshtein";
            case LCS -> "lcs";
            case TFIDF -> "tfidf";
            case JACCARD -> "jaccard";
            case FASTTEXT -> "fasttext";
            case SENTENCE_BERT -> "sentence-bert";
        };
    }

    public SimilarityAlgorithmDto toDto(String algorithm) {
        return switch (algorithm) {
            case "levenshtein" -> SimilarityAlgorithmDto.LEVENSHTEIN;
            case "lcs" -> SimilarityAlgorithmDto.LCS;
            case "tfidf" -> SimilarityAlgorithmDto.TFIDF;
            case "jaccard" -> SimilarityAlgorithmDto.JACCARD;
            case "fasttext" -> SimilarityAlgorithmDto.FASTTEXT;
            case "sentence-bert" -> SimilarityAlgorithmDto.SENTENCE_BERT;
            default -> throw new IllegalArgumentException("Algoritmo de similitud no soportado.");
        };
    }

    private int maxLabel(int[] labels) {
        int max = -1;
        for (int label : labels) {
            max = Math.max(max, label);
        }
        return max;
    }
}
