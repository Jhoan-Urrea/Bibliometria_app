package com.bibliometria.core.retrieval;

import java.util.Objects;

/** Inmutable, paginated query contract. */
public record SearchQuery(String text, int page, int pageSize) {

    public static final int MAX_PAGE_SIZE = 100;

    public SearchQuery {
        Objects.requireNonNull(text, "text");
        text = text.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Search text must not be blank.");
        }
        if (page < 1) {
            throw new IllegalArgumentException("Page numbers start at 1.");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
    }

    public SearchQuery(String text) {
        this(text, 1, 10);
    }
}
