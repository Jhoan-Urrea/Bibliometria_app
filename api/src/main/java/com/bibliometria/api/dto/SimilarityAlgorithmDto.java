package com.bibliometria.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum SimilarityAlgorithmDto {
    @JsonProperty("levenshtein")
    LEVENSHTEIN,
    @JsonProperty("lcs")
    LCS,
    @JsonProperty("tfidf")
    TFIDF,
    @JsonProperty("jaccard")
    JACCARD,
    @JsonProperty("fasttext")
    FASTTEXT,
    @JsonProperty("sentence-bert")
    SENTENCE_BERT
}
