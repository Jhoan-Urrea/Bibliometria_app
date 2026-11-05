package com.bibliometria.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.bibliometria.api.dto.ArticleDto;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiContractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;

    @Test
    void exposesAnalysisEndpointsAndProblemDetails() {
        ResponseEntity<List<ArticleDto>> articles =
                restTemplate.exchange(
                        "/api/articles",
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<>() {});
        assertThat(articles.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(articles.getBody()).hasSize(20);

        ResponseEntity<JsonNode> similarity =
                post(
                        "/api/similarity",
                        Map.of(
                                "articleAId", "A01",
                                "articleBId", "A02",
                                "algorithm", "levenshtein"));
        assertThat(similarity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(similarity.getBody().path("normalizedScore").asDouble()).isBetween(0.0, 1.0);

        ResponseEntity<JsonNode> clustering =
                post("/api/clustering", Map.of("clusterCount", 3, "linkage", "average"));
        assertThat(clustering.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(clustering.getBody().path("clusters").size()).isEqualTo(3);
        assertThat(clustering.getBody().path("merges").size()).isEqualTo(19);
        assertThat(clustering.getBody().path("linkage").asText()).isEqualTo("average");

        ResponseEntity<JsonNode> search =
                post("/api/search", Map.of("query", "artificial intelligence", "pageSize", 3));
        assertThat(search.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(search.getBody().path("results").size()).isLessThanOrEqualTo(3);

        ResponseEntity<JsonNode> trace =
                restTemplate.getForEntity("/api/search/trace?pattern=intelligence", JsonNode.class);
        assertThat(trace.getStatusCode())
                .as("Search trace response: %s", trace.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(trace.getBody().path("algorithmsAgree").asBoolean()).isTrue();
        assertThat(trace.getBody().path("articles").size()).isEqualTo(20);
        assertThat(trace.getBody().path("articles").get(0).path("events").size()).isZero();

        ResponseEntity<JsonNode> articleTrace =
                restTemplate.getForEntity(
                        "/api/search/trace?pattern=intelligence&articleId=A01", JsonNode.class);
        assertThat(articleTrace.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(articleTrace.getBody().path("articles").size()).isEqualTo(1);
        assertThat(articleTrace.getBody().path("articles").get(0).path("events").size())
                .isPositive();

        ResponseEntity<JsonNode> notFound =
                post(
                        "/api/similarity",
                        Map.of(
                                "articleAId", "A99",
                                "articleBId", "A02",
                                "algorithm", "levenshtein"));
        assertProblem(notFound, HttpStatus.NOT_FOUND);

        ResponseEntity<JsonNode> unavailableAlgorithm =
                post(
                        "/api/similarity",
                        Map.of(
                                "articleAId", "A01",
                                "articleBId", "A02",
                                "algorithm", "sentence-bert"));
        assertProblem(unavailableAlgorithm, HttpStatus.UNPROCESSABLE_ENTITY);

        ResponseEntity<JsonNode> invalid = post("/api/search", Map.of("query", "  "));
        assertProblem(invalid, HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody().path("invalidParams").size()).isEqualTo(1);

        ResponseEntity<JsonNode> invalidTrace =
                restTemplate.getForEntity("/api/search/trace?pattern=", JsonNode.class);
        assertProblem(invalidTrace, HttpStatus.BAD_REQUEST);

        ResponseEntity<JsonNode> missingTraceArticle =
                restTemplate.getForEntity(
                        "/api/search/trace?pattern=intelligence&articleId=A99", JsonNode.class);
        assertProblem(missingTraceArticle, HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<JsonNode> post(String path, Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                path, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private void assertProblem(ResponseEntity<JsonNode> response, HttpStatus status) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getHeaders().getFirst("X-Correlation-ID"))
                .isEqualTo(response.getBody().path("correlationId").asText());
        assertThat(response.getBody().path("status").asInt()).isEqualTo(status.value());
    }
}
