package com.bibliometria.tools.benchmark;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Analiza las muestras CSV del módulo benchmark y genera tablas reproducibles en Markdown. */
public final class AnalisisBenchmark {

    private static final int MINIMUM_SAMPLES = 30;
    private static final List<String> REQUIRED_COLUMNS =
            List.of(
                    "escenario",
                    "algoritmo",
                    "n",
                    "m",
                    "sigma",
                    "repeticion",
                    "nanosPreproceso",
                    "nanosBusqueda",
                    "comparaciones",
                    "extras");

    private AnalisisBenchmark() {}

    /**
     * Lee un CSV del benchmark y calcula medianas, IQR, regresiones log-log y métricas de costo.
     *
     * @param input CSV de muestras
     * @return resultados ordenados y validados
     * @throws IOException si el CSV no se puede leer o su esquema/datos no son válidos
     */
    public static AnalysisReport analyze(Path input) throws IOException {
        List<Sample> samples = readSamples(input);
        Map<GroupKey, List<Sample>> grouped = new TreeMap<>();
        samples.forEach(
                sample ->
                        grouped.computeIfAbsent(sample.groupKey(), ignored -> new ArrayList<>())
                                .add(sample));
        List<GroupStatistics> statistics = new ArrayList<>();
        for (Map.Entry<GroupKey, List<Sample>> entry : grouped.entrySet()) {
            Set<Integer> repetitions = new HashSet<>();
            for (Sample sample : entry.getValue()) {
                if (!repetitions.add(sample.repetition())) {
                    throw new IOException(
                            "Repetición duplicada en el grupo " + entry.getKey() + ".");
                }
            }
            if (entry.getValue().size() < MINIMUM_SAMPLES) {
                throw new IOException(
                        "El grupo "
                                + entry.getKey()
                                + " contiene "
                                + entry.getValue().size()
                                + " muestras; se requieren al menos "
                                + MINIMUM_SAMPLES
                                + ".");
            }
            statistics.add(summarize(entry.getKey(), entry.getValue()));
        }
        return new AnalysisReport(
                List.copyOf(statistics),
                regressionsByN(statistics),
                regressionsByPatternLength(statistics));
    }

    /** CLI: {@code AnalisisBenchmark <muestras.csv> [informe.md]}. */
    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException(
                    "Uso: AnalisisBenchmark <muestras.csv> [informe.md]");
        }
        AnalysisReport report = analyze(Path.of(args[0]));
        if (args.length == 2) {
            Path output = Path.of(args[1]);
            Path parent = output.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (BufferedWriter writer = Files.newBufferedWriter(output)) {
                writer.write(report.toMarkdown());
            }
        } else {
            System.out.print(report.toMarkdown());
        }
    }

    private static List<Sample> readSamples(Path input) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(input)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IOException("El CSV está vacío.");
            }
            List<String> header = parseCsvLine(headerLine);
            if (!header.equals(REQUIRED_COLUMNS)) {
                throw new IOException(
                        "El encabezado CSV no coincide con el contrato esperado: " + header);
            }
            List<Sample> samples = new ArrayList<>();
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                List<String> values = parseCsvLine(line);
                if (values.size() != REQUIRED_COLUMNS.size()) {
                    throw new IOException("Cantidad de columnas inválida en línea " + lineNumber);
                }
                try {
                    Sample sample =
                            new Sample(
                                    values.get(0),
                                    values.get(1),
                                    parsePositiveInt(values.get(2), "n"),
                                    parsePositiveInt(values.get(3), "m"),
                                    parsePositiveInt(values.get(4), "sigma"),
                                    parsePositiveInt(values.get(5), "repeticion"),
                                    parseNonNegativeLong(values.get(6), "nanosPreproceso"),
                                    parseNonNegativeLong(values.get(7), "nanosBusqueda"),
                                    parseNonNegativeLong(values.get(8), "comparaciones"),
                                    values.get(9));
                    if (sample.m() > sample.n()) {
                        throw new IllegalArgumentException("m no puede ser mayor que n.");
                    }
                    samples.add(sample);
                } catch (IllegalArgumentException exception) {
                    throw new IOException(
                            "Dato inválido en línea " + lineNumber + ": " + exception.getMessage(),
                            exception);
                }
            }
            if (samples.isEmpty()) {
                throw new IOException("El CSV no contiene muestras.");
            }
            return List.copyOf(samples);
        }
    }

    private static List<String> parseCsvLine(String line) throws IOException {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean closedQuote = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (quoted) {
                if (character == '"') {
                    if (index + 1 < line.length() && line.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                        closedQuote = true;
                    }
                } else {
                    field.append(character);
                }
            } else if (character == ',' && !quoted) {
                fields.add(field.toString());
                field.setLength(0);
                closedQuote = false;
            } else if (character == '"' && field.isEmpty() && !closedQuote) {
                quoted = true;
            } else if (closedQuote) {
                throw new IOException("Contenido inesperado después de un campo entrecomillado.");
            } else {
                field.append(character);
            }
        }
        if (quoted) {
            throw new IOException("Campo CSV entrecomillado sin cierre.");
        }
        fields.add(field.toString());
        return List.copyOf(fields);
    }

    private static int parsePositiveInt(String value, String name) {
        int parsed = Integer.parseInt(value);
        if (parsed < 1) {
            throw new IllegalArgumentException(name + " debe ser positivo.");
        }
        return parsed;
    }

    private static long parseNonNegativeLong(String value, String name) {
        long parsed = Long.parseLong(value);
        if (parsed < 0) {
            throw new IllegalArgumentException(name + " no puede ser negativo.");
        }
        return parsed;
    }

    private static GroupStatistics summarize(GroupKey key, List<Sample> samples) {
        List<Double> preprocessing =
                samples.stream().map(sample -> (double) sample.preprocessingNanos()).toList();
        List<Double> search =
                samples.stream().map(sample -> (double) sample.searchNanos()).toList();
        List<Double> total =
                samples.stream()
                        .map(sample -> (double) sample.preprocessingNanos() + sample.searchNanos())
                        .toList();
        List<Double> comparisons =
                samples.stream().map(sample -> (double) sample.comparisons()).toList();
        List<Double> preprocessingFractions =
                samples.stream()
                        .map(
                                sample -> {
                                    double sampleTotal =
                                            (double) sample.preprocessingNanos()
                                                    + sample.searchNanos();
                                    return sampleTotal == 0
                                            ? Double.NaN
                                            : sample.preprocessingNanos() / sampleTotal;
                                })
                        .filter(Double::isFinite)
                        .toList();
        double medianComparisons = median(comparisons);
        double theoreticalE3 = (double) key.n() * key.m();
        double e3Ratio =
                key.scenario().startsWith("E3") ? medianComparisons / theoreticalE3 : Double.NaN;
        return new GroupStatistics(
                key,
                samples.size(),
                median(preprocessing),
                median(search),
                median(total),
                iqr(preprocessing),
                iqr(search),
                iqr(comparisons),
                median(comparisons),
                medianComparisons / key.n(),
                preprocessingFractions.isEmpty() ? Double.NaN : median(preprocessingFractions),
                e3Ratio);
    }

    private static List<Regression> regressionsByN(List<GroupStatistics> groups) {
        Map<RegressionKey, List<GroupStatistics>> grouped = new TreeMap<>();
        for (GroupStatistics group : groups) {
            RegressionKey key =
                    new RegressionKey(
                            group.key().scenario(),
                            group.key().algorithm(),
                            group.key().m(),
                            group.key().sigma());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(group);
        }
        return grouped.entrySet().stream()
                .map(entry -> regress(entry.getKey(), entry.getValue(), Axis.N))
                .filter(regression -> regression.points() >= 2)
                .toList();
    }

    private static List<Regression> regressionsByPatternLength(List<GroupStatistics> groups) {
        Map<RegressionKey, List<GroupStatistics>> grouped = new TreeMap<>();
        for (GroupStatistics group : groups) {
            RegressionKey key =
                    new RegressionKey(
                            group.key().scenario(),
                            group.key().algorithm(),
                            group.key().n(),
                            group.key().sigma());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(group);
        }
        return grouped.entrySet().stream()
                .map(entry -> regress(entry.getKey(), entry.getValue(), Axis.M))
                .filter(regression -> regression.points() >= 2)
                .toList();
    }

    private static Regression regress(RegressionKey key, List<GroupStatistics> groups, Axis axis) {
        List<GroupStatistics> points =
                groups.stream()
                        .filter(group -> group.medianTotalNanos() > 0)
                        .sorted(
                                Comparator.comparingInt(
                                        group ->
                                                axis == Axis.N ? group.key().n() : group.key().m()))
                        .toList();
        List<GroupStatistics> distinct =
                points.stream()
                        .collect(
                                Collectors.collectingAndThen(
                                        Collectors.toMap(
                                                group ->
                                                        axis == Axis.N
                                                                ? group.key().n()
                                                                : group.key().m(),
                                                group -> group,
                                                (first, ignored) -> first,
                                                LinkedHashMap::new),
                                        map -> List.copyOf(map.values())));
        if (distinct.size() < 2) {
            return new Regression(key, axis, Double.NaN, Double.NaN, distinct.size());
        }
        double[] x =
                distinct.stream()
                        .mapToDouble(
                                group ->
                                        Math.log(
                                                axis == Axis.N ? group.key().n() : group.key().m()))
                        .toArray();
        double[] y =
                distinct.stream()
                        .mapToDouble(group -> Math.log(group.medianTotalNanos()))
                        .toArray();
        return linearRegression(key, axis, x, y);
    }

    private static Regression linearRegression(
            RegressionKey key, Axis axis, double[] x, double[] y) {
        double meanX = mean(x);
        double meanY = mean(y);
        double covariance = 0;
        double varianceX = 0;
        double varianceY = 0;
        for (int index = 0; index < x.length; index++) {
            double dx = x[index] - meanX;
            double dy = y[index] - meanY;
            covariance += dx * dy;
            varianceX += dx * dx;
            varianceY += dy * dy;
        }
        if (varianceX == 0) {
            return new Regression(key, axis, Double.NaN, Double.NaN, x.length);
        }
        double slope = covariance / varianceX;
        double rSquared = varianceY == 0 ? 1 : (covariance * covariance) / (varianceX * varianceY);
        return new Regression(key, axis, slope, rSquared, x.length);
    }

    private static double mean(double[] values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    private static double median(List<Double> values) {
        return quantile(values, 0.5);
    }

    private static double iqr(List<Double> values) {
        return quantile(values, 0.75) - quantile(values, 0.25);
    }

    private static double quantile(List<Double> values, double probability) {
        List<Double> sorted = values.stream().sorted().toList();
        double position = probability * (sorted.size() - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        return sorted.get(lower) + (sorted.get(upper) - sorted.get(lower)) * (position - lower);
    }

    public record AnalysisReport(
            List<GroupStatistics> groups,
            List<Regression> timeRegressionsByN,
            List<Regression> timeRegressionsByM) {

        public AnalysisReport {
            groups = List.copyOf(groups);
            timeRegressionsByN = List.copyOf(timeRegressionsByN);
            timeRegressionsByM = List.copyOf(timeRegressionsByM);
        }

        public String toMarkdown() {
            StringBuilder markdown = new StringBuilder();
            markdown.append("# Análisis estadístico de benchmark\n\n");
            markdown.append("## Estadísticos por caso\n\n");
            markdown.append(
                    "| Escenario | Algoritmo | n | m | σ | Muestras | Mediana pre (ns) | IQR pre (ns) | Mediana búsqueda (ns) | IQR búsqueda (ns) | Mediana total (ns) | Comparaciones | IQR comparaciones | Comparaciones/n | Fracción pre | C/(n·m) E3 |\n");
            markdown.append(
                    "|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
            groups.forEach(
                    group ->
                            markdown.append("| ")
                                    .append(group.key().scenario())
                                    .append(" | ")
                                    .append(group.key().algorithm())
                                    .append(" | ")
                                    .append(group.key().n())
                                    .append(" | ")
                                    .append(group.key().m())
                                    .append(" | ")
                                    .append(group.key().sigma())
                                    .append(" | ")
                                    .append(group.samples())
                                    .append(" | ")
                                    .append(format(group.medianPreprocessingNanos()))
                                    .append(" | ")
                                    .append(format(group.iqrPreprocessingNanos()))
                                    .append(" | ")
                                    .append(format(group.medianSearchNanos()))
                                    .append(" | ")
                                    .append(format(group.iqrSearchNanos()))
                                    .append(" | ")
                                    .append(format(group.medianTotalNanos()))
                                    .append(" | ")
                                    .append(format(group.medianComparisons()))
                                    .append(" | ")
                                    .append(format(group.iqrComparisons()))
                                    .append(" | ")
                                    .append(format(group.medianComparisonsPerCharacter()))
                                    .append(" | ")
                                    .append(formatPercent(group.preprocessingFraction()))
                                    .append(" | ")
                                    .append(format(group.e3NmnRatio()))
                                    .append(" |\n"));
            appendRegressions(markdown, "Pendiente tiempo vs. n (log-log)", timeRegressionsByN);
            appendRegressions(markdown, "Pendiente tiempo vs. m (log-log)", timeRegressionsByM);
            markdown.append(
                    "\n`C/(n·m) E3` es la razón entre la mediana de comparaciones observada y el producto n·m; solo aplica al escenario E3. La columna `Comparaciones/n` muestra el costo por carácter del texto para cada longitud de patrón m. La fracción de preprocesamiento es la mediana de `nanosPreproceso / (nanosPreproceso + nanosBusqueda)` por muestra.\n");
            return markdown.toString();
        }

        private static void appendRegressions(
                StringBuilder markdown, String title, List<Regression> regressions) {
            markdown.append("\n## ").append(title).append("\n\n");
            markdown.append("| Escenario | Algoritmo | Tamaño fijo | σ | Puntos | b | R² |\n");
            markdown.append("|---|---|---:|---:|---:|---:|---:|\n");
            regressions.forEach(
                    regression ->
                            markdown.append("| ")
                                    .append(regression.key().scenario())
                                    .append(" | ")
                                    .append(regression.key().algorithm())
                                    .append(" | ")
                                    .append(regression.axis() == Axis.N ? "m=" : "n=")
                                    .append(regression.key().control())
                                    .append(" | ")
                                    .append(regression.key().sigma())
                                    .append(" | ")
                                    .append(regression.points())
                                    .append(" | ")
                                    .append(format(regression.slope()))
                                    .append(" | ")
                                    .append(format(regression.rSquared()))
                                    .append(" |\n"));
            if (regressions.isEmpty()) {
                markdown.append("| — | — | — | — | — | — | — |\n");
                markdown.append(
                        "\nNo hay al menos dos tamaños distintos con tiempo mediano positivo para estimar la pendiente.\n");
            }
        }

        private static String formatPercent(double value) {
            return Double.isFinite(value) ? String.format("%.2f%%", value * 100) : "—";
        }

        private static String format(double value) {
            return Double.isFinite(value) ? String.format("%.4f", value) : "—";
        }
    }

    public record GroupStatistics(
            GroupKey key,
            int samples,
            double medianPreprocessingNanos,
            double medianSearchNanos,
            double medianTotalNanos,
            double iqrPreprocessingNanos,
            double iqrSearchNanos,
            double iqrComparisons,
            double medianComparisons,
            double medianComparisonsPerCharacter,
            double preprocessingFraction,
            double e3NmnRatio) {}

    public record Regression(
            RegressionKey key, Axis axis, double slope, double rSquared, int points) {}

    public record GroupKey(String scenario, String algorithm, int n, int m, int sigma)
            implements Comparable<GroupKey> {
        @Override
        public int compareTo(GroupKey other) {
            int value = scenario.compareTo(other.scenario);
            if (value == 0) {
                value = algorithm.compareTo(other.algorithm);
            }
            if (value == 0) {
                value = Integer.compare(sigma, other.sigma);
            }
            if (value == 0) {
                value = Integer.compare(m, other.m);
            }
            if (value == 0) {
                value = Integer.compare(n, other.n);
            }
            return value;
        }
    }

    public record RegressionKey(String scenario, String algorithm, int control, int sigma)
            implements Comparable<RegressionKey> {
        @Override
        public int compareTo(RegressionKey other) {
            int value = scenario.compareTo(other.scenario);
            if (value == 0) {
                value = algorithm.compareTo(other.algorithm);
            }
            if (value == 0) {
                value = Integer.compare(sigma, other.sigma);
            }
            if (value == 0) {
                value = Integer.compare(control, other.control);
            }
            return value;
        }
    }

    public enum Axis {
        N,
        M
    }

    private record Sample(
            String scenario,
            String algorithm,
            int n,
            int m,
            int sigma,
            int repetition,
            long preprocessingNanos,
            long searchNanos,
            long comparisons,
            String extras) {

        GroupKey groupKey() {
            return new GroupKey(scenario, algorithm, n, m, sigma);
        }
    }
}
