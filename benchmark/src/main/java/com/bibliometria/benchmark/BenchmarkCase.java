package com.bibliometria.benchmark;

import java.util.Objects;

/** Un caso determinista que compara algoritmos sobre el mismo texto y patrón. */
public record BenchmarkCase(String scenario, String text, String pattern, int sigma) {

    public BenchmarkCase {
        Objects.requireNonNull(scenario, "scenario");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(pattern, "pattern");
        if (scenario.isBlank()
                || text.isEmpty()
                || pattern.isEmpty()
                || pattern.length() > text.length()) {
            throw new IllegalArgumentException(
                    "El caso requiere escenario, texto y patrón válidos.");
        }
        if (sigma < 1) {
            throw new IllegalArgumentException("sigma debe ser mayor que cero.");
        }
    }

    public int n() {
        return text.length();
    }

    public int m() {
        return pattern.length();
    }
}
