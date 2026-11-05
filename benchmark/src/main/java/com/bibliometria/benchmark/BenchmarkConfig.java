package com.bibliometria.benchmark;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Configuración JSON para ejecutar escenarios y escribir resultados reproducibles. */
public record BenchmarkConfig(
        long seed,
        int samples,
        Path corpus,
        Path outputCsv,
        Path summaryCsv,
        List<ScenarioSpec> scenarios) {

    public BenchmarkConfig {
        Objects.requireNonNull(outputCsv, "outputCsv");
        Objects.requireNonNull(summaryCsv, "summaryCsv");
        Objects.requireNonNull(scenarios, "scenarios");
        scenarios = List.copyOf(scenarios);
        if (samples < BenchmarkRunner.MINIMUM_SAMPLES) {
            throw new IllegalArgumentException(
                    "La metodología requiere al menos "
                            + BenchmarkRunner.MINIMUM_SAMPLES
                            + " muestras.");
        }
        if (scenarios.isEmpty()) {
            throw new IllegalArgumentException("Debe configurarse al menos un escenario.");
        }
    }

    /** Parámetros comunes de los generadores E1, E2 y E3. */
    public record ScenarioSpec(
            ScenarioType type,
            List<Integer> textSizes,
            List<Integer> patternLengths,
            List<Integer> alphabetSizes,
            String presentPattern,
            String absentPattern) {

        public ScenarioSpec {
            Objects.requireNonNull(type, "type");
            textSizes = textSizes == null ? List.of() : List.copyOf(textSizes);
            patternLengths = patternLengths == null ? List.of() : List.copyOf(patternLengths);
            alphabetSizes = alphabetSizes == null ? List.of() : List.copyOf(alphabetSizes);
        }
    }
}
