package com.bibliometria.tools.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalisisBenchmarkTest {

    @TempDir Path tempDirectory;

    @Test
    void summarizesSamplesCalculatesLogRegressionsAndE3Metrics() throws Exception {
        Path csv = writeFixture();

        AnalisisBenchmark.AnalysisReport report = AnalisisBenchmark.analyze(csv);

        assertThat(report.groups()).hasSize(3);
        assertThat(report.timeRegressionsByN())
                .singleElement()
                .satisfies(
                        regression -> {
                            assertThat(regression.axis()).isEqualTo(AnalisisBenchmark.Axis.N);
                            assertThat(regression.slope()).isCloseTo(1.0, Offset.offset(1.0e-10));
                            assertThat(regression.rSquared())
                                    .isCloseTo(1.0, Offset.offset(1.0e-10));
                        });
        assertThat(report.timeRegressionsByM())
                .singleElement()
                .satisfies(
                        regression ->
                                assertThat(regression.slope())
                                        .isCloseTo(1.0, Offset.offset(1.0e-10)));

        AnalisisBenchmark.GroupStatistics firstGroup = report.groups().getFirst();
        assertThat(firstGroup.samples()).isEqualTo(30);
        assertThat(firstGroup.preprocessingFraction()).isCloseTo(0.1, Offset.offset(1.0e-10));
        assertThat(firstGroup.medianComparisonsPerCharacter()).isEqualTo(10.0);
        assertThat(firstGroup.e3NmnRatio()).isEqualTo(1.0);
        assertThat(report.toMarkdown())
                .contains("Pendiente tiempo vs. n (log-log)")
                .contains("C/(n·m) E3")
                .contains("IQR comparaciones");
    }

    @Test
    void rejectsGroupsWithFewerThanThirtySamplesAndDuplicateRepetitions() throws Exception {
        Path insufficient = tempDirectory.resolve("insufficient.csv");
        Files.writeString(
                insufficient,
                header()
                        + row("E3", "Rabin-Karp", 100, 10, 1, 1, 1, 9, 100)
                        + row("E3", "Rabin-Karp", 100, 10, 1, 2, 1, 9, 100));
        assertThatThrownBy(() -> AnalisisBenchmark.analyze(insufficient))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("al menos 30");

        Path duplicate = tempDirectory.resolve("duplicate.csv");
        StringBuilder rows = new StringBuilder(header());
        for (int repetition = 0; repetition < 30; repetition++) {
            rows.append(row("E3", "Rabin-Karp", 100, 10, 1, 1, 1, 9, 100));
        }
        Files.writeString(duplicate, rows);
        assertThatThrownBy(() -> AnalisisBenchmark.analyze(duplicate))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("Repetición duplicada");
    }

    private Path writeFixture() throws Exception {
        StringBuilder csv = new StringBuilder(header());
        appendSamples(csv, 100, 10, 100, 900, 1_000);
        appendSamples(csv, 200, 10, 200, 1_800, 2_000);
        appendSamples(csv, 200, 20, 400, 3_600, 4_000);
        Path file = tempDirectory.resolve("samples.csv");
        Files.writeString(file, csv);
        return file;
    }

    private static void appendSamples(
            StringBuilder csv,
            int n,
            int m,
            long preprocessing,
            long search,
            long totalComparisons) {
        for (int repetition = 1; repetition <= 30; repetition++) {
            csv.append(
                    row(
                            "E3",
                            "Rabin-Karp",
                            n,
                            m,
                            1,
                            repetition,
                            preprocessing,
                            search,
                            totalComparisons));
        }
    }

    private static String header() {
        return "\"escenario\",\"algoritmo\",\"n\",\"m\",\"sigma\",\"repeticion\","
                + "\"nanosPreproceso\",\"nanosBusqueda\",\"comparaciones\",\"extras\"\n";
    }

    private static String row(
            String scenario,
            String algorithm,
            int n,
            int m,
            int sigma,
            int repetition,
            long preprocessing,
            long search,
            long comparisons) {
        return String.format(
                "\"%s\",\"%s\",\"%d\",\"%d\",\"%d\",\"%d\",\"%d\",\"%d\","
                        + "\"%d\",\"{\"\"hashCoincidencias\"\":%d,\"\"falsosPositivos\"\":0}\"%n",
                scenario,
                algorithm,
                n,
                m,
                sigma,
                repetition,
                preprocessing,
                search,
                comparisons,
                n - m + 1);
    }
}
