package com.bibliometria.api.dto;

import java.util.List;
import java.util.Map;

public record SearchTraceResponse(
        String pattern, boolean algorithmsAgree, List<ArticleTrace> articles) {

    public SearchTraceResponse {
        articles = List.copyOf(articles);
    }

    public record ArticleTrace(
            String articleId,
            Map<String, List<Integer>> positionsByAlgorithm,
            List<MatchFragment> fragments,
            List<TraceEvent> events) {

        public ArticleTrace {
            positionsByAlgorithm =
                    positionsByAlgorithm.entrySet().stream()
                            .collect(
                                    java.util.stream.Collectors.toUnmodifiableMap(
                                            Map.Entry::getKey,
                                            entry -> List.copyOf(entry.getValue())));
            fragments = List.copyOf(fragments);
            events = List.copyOf(events);
        }
    }

    public record MatchFragment(int start, String text, int highlightStart, int highlightEnd) {}

    public record TraceEvent(
            String articleId, String algorithm, String step, Map<String, Object> data) {
        public TraceEvent {
            data = Map.copyOf(data);
        }
    }
}
