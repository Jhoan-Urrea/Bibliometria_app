package com.bibliometria.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScenarioGeneratorTest {

    @TempDir Path tempDirectory;

    @Test
    void createsRealCasesForPresentAndAbsentPatterns() throws Exception {
        Path corpus = tempDirectory.resolve("corpus.json");
        Files.writeString(
                corpus,
                """
                [
                  {"abstractText":"artificial intelligence changes research"},
                  {"abstractText":"bibliometric analysis"}
                ]
                """);
        BenchmarkConfig config =
                config(
                        corpus,
                        new BenchmarkConfig.ScenarioSpec(
                                ScenarioType.E1,
                                List.of(),
                                List.of(),
                                List.of(),
                                "artificial intelligence",
                                "not-in-the-corpus"));

        List<BenchmarkCase> cases =
                ScenarioGenerator.generate(config, tempDirectory, new ObjectMapper());

        assertThat(cases)
                .extracting(BenchmarkCase::scenario)
                .containsExactly("E1-present", "E1-absent");
        assertThat(cases.getFirst().text()).contains("bibliometric analysis");
        assertThat(cases.getFirst().sigma()).isPositive();
    }

    @Test
    void createsReproducibleSyntheticCasesAndAdversarialRepeatedInput() throws Exception {
        BenchmarkConfig.ScenarioSpec synthetic =
                new BenchmarkConfig.ScenarioSpec(
                        ScenarioType.E2, List.of(64), List.of(8), List.of(4), null, null);
        BenchmarkConfig.ScenarioSpec adversarial =
                new BenchmarkConfig.ScenarioSpec(
                        ScenarioType.E3, List.of(64), List.of(8), List.of(), null, null);
        BenchmarkConfig firstConfig =
                new BenchmarkConfig(
                        42,
                        30,
                        null,
                        Path.of("samples.csv"),
                        Path.of("summary.csv"),
                        List.of(synthetic, adversarial));
        BenchmarkConfig secondConfig =
                new BenchmarkConfig(
                        42,
                        30,
                        null,
                        Path.of("samples.csv"),
                        Path.of("summary.csv"),
                        List.of(synthetic, adversarial));

        List<BenchmarkCase> first =
                ScenarioGenerator.generate(firstConfig, tempDirectory, new ObjectMapper());
        List<BenchmarkCase> second =
                ScenarioGenerator.generate(secondConfig, tempDirectory, new ObjectMapper());

        assertThat(first.getFirst()).isEqualTo(second.getFirst());
        assertThat(first.getFirst().n()).isEqualTo(64);
        assertThat(first.getFirst().sigma()).isEqualTo(4);
        assertThat(first.get(1).text()).isEqualTo("a".repeat(64));
        assertThat(first.get(1).pattern()).isEqualTo("a".repeat(8));
        assertThat(first.get(1).sigma()).isEqualTo(1);
    }

    @Test
    void requiresAtLeastThirtySamples() {
        assertThatThrownBy(
                        () ->
                                new BenchmarkConfig(
                                        42,
                                        29,
                                        null,
                                        Path.of("samples.csv"),
                                        Path.of("summary.csv"),
                                        List.of(
                                                new BenchmarkConfig.ScenarioSpec(
                                                        ScenarioType.E3,
                                                        List.of(8),
                                                        List.of(2),
                                                        List.of(),
                                                        null,
                                                        null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("al menos 30");
    }

    private BenchmarkConfig config(Path corpus, BenchmarkConfig.ScenarioSpec scenario) {
        return new BenchmarkConfig(
                42, 30, corpus, Path.of("samples.csv"), Path.of("summary.csv"), List.of(scenario));
    }
}
