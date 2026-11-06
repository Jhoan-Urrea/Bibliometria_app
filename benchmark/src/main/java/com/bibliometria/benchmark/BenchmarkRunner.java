package com.bibliometria.benchmark;

import com.bibliometria.core.matching.BoyerMooreMatcher;
import com.bibliometria.core.matching.KmpMatcher;
import com.bibliometria.core.matching.MatchResult;
import com.bibliometria.core.matching.RabinKarpMatcher;
import com.bibliometria.core.matching.StringMatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Harness reproducible para medir KMP, Boyer-Moore y Rabin-Karp. */
public final class BenchmarkRunner {

    public static final int WARMUP_RUNS = 2_000;
    public static final int MINIMUM_SAMPLES = 30;

    private static volatile long blackhole;

    private BenchmarkRunner() {}

    /**
     * Ejecuta el benchmark usando un archivo JSON indicado con {@code --config}.
     *
     * @param args argumentos de línea de comandos
     * @throws IOException cuando la configuración o los CSV no se pueden leer o escribir
     */
    public static void main(String[] args) throws IOException {
        Path configPath = parseConfigPath(args).toAbsolutePath().normalize();
        ObjectMapper objectMapper = new ObjectMapper();
        BenchmarkConfig config = objectMapper.readValue(configPath.toFile(), BenchmarkConfig.class);
        Path configDirectory = configPath.getParent();
        List<BenchmarkCase> cases =
                ScenarioGenerator.generate(config, configDirectory, objectMapper);
        Path rawOutput = resolve(configDirectory, config.outputCsv()).toAbsolutePath().normalize();
        Path summaryOutput =
                resolve(configDirectory, config.summaryCsv()).toAbsolutePath().normalize();
        if (rawOutput.equals(summaryOutput)) {
            throw new IllegalArgumentException(
                    "Los CSV de muestras y resumen deben ser distintos.");
        }
        createParentDirectory(rawOutput);
        createParentDirectory(summaryOutput);
        run(config, cases, rawOutput, summaryOutput, objectMapper);
    }

    static void run(
            BenchmarkConfig config,
            List<BenchmarkCase> cases,
            Path rawOutput,
            Path summaryOutput,
            ObjectMapper objectMapper)
            throws IOException {
        Random orderRandom = new Random(config.seed());
        Files.deleteIfExists(summaryOutput);
        long sampleRows = 0;
        try (BufferedWriter writer = Files.newBufferedWriter(rawOutput)) {
            writeCsvRow(
                    writer,
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
                            "extras"));
            int caseIndex = 0;
            for (BenchmarkCase benchmarkCase : cases) {
                runWarmup(benchmarkCase, orderRandom);
                EnumMap<Algorithm, List<MatchResult>> caseSamples = new EnumMap<>(Algorithm.class);
                for (Algorithm algorithm : Algorithm.values()) {
                    caseSamples.put(algorithm, new ArrayList<>(config.samples()));
                }
                for (int repetition = 1; repetition <= config.samples(); repetition++) {
                    List<Algorithm> order = new ArrayList<>(List.of(Algorithm.values()));
                    java.util.Collections.shuffle(order, orderRandom);
                    Map<Algorithm, List<Integer>> positionsByAlgorithm =
                            new EnumMap<>(Algorithm.class);
                    for (Algorithm algorithm : order) {
                        MatchResult result =
                                algorithm
                                        .matcher()
                                        .buscarTodas(benchmarkCase.text(), benchmarkCase.pattern());
                        checkAgreement(positionsByAlgorithm, algorithm, result);
                        caseSamples.get(algorithm).add(result);
                        writeCsvRow(
                                writer,
                                List.of(
                                        benchmarkCase.scenario(),
                                        algorithm.csvName,
                                        Integer.toString(benchmarkCase.n()),
                                        Integer.toString(benchmarkCase.m()),
                                        Integer.toString(benchmarkCase.sigma()),
                                        Integer.toString(repetition),
                                        Long.toString(result.nanosPreproceso()),
                                        Long.toString(result.nanosBusqueda()),
                                        Long.toString(result.comparaciones()),
                                        objectMapper.writeValueAsString(result.extras())));
                        updateBlackhole(result);
                        sampleRows++;
                    }
                }
                caseIndex++;
                System.err.printf(
                        "Caso %d/%d terminado: %s, n=%d, m=%d, sigma=%d%n",
                        caseIndex,
                        cases.size(),
                        benchmarkCase.scenario(),
                        benchmarkCase.n(),
                        benchmarkCase.m(),
                        benchmarkCase.sigma());
                writeSummary(summaryOutput, benchmarkCase, caseSamples);
            }
        }
        System.err.printf(
                "Benchmark completado: %d filas de muestra; CSV=%s; resumen=%s%n",
                sampleRows, rawOutput, summaryOutput);
    }

    private static void runWarmup(BenchmarkCase benchmarkCase, Random random) {
        List<Algorithm> order = new ArrayList<>(List.of(Algorithm.values()));
        for (int run = 0; run < WARMUP_RUNS; run++) {
            java.util.Collections.shuffle(order, random);
            for (Algorithm algorithm : order) {
                MatchResult discarded =
                        algorithm
                                .matcher()
                                .buscarTodas(benchmarkCase.text(), benchmarkCase.pattern());
                updateBlackhole(discarded);
            }
        }
    }

    private static void checkAgreement(
            Map<Algorithm, List<Integer>> positionsByAlgorithm,
            Algorithm algorithm,
            MatchResult result) {
        if (!positionsByAlgorithm.isEmpty()
                && !positionsByAlgorithm.values().iterator().next().equals(result.posiciones())) {
            throw new IllegalStateException(
                    "Los algoritmos no coinciden en las posiciones para el caso de benchmark.");
        }
        positionsByAlgorithm.put(algorithm, result.posiciones());
    }

    private static void updateBlackhole(MatchResult result) {
        blackhole =
                blackhole
                        ^ result.comparaciones()
                        ^ result.posiciones().size()
                        ^ result.nanosPreproceso()
                        ^ result.nanosBusqueda();
    }

    private static void writeSummary(
            Path output,
            BenchmarkCase benchmarkCase,
            EnumMap<Algorithm, List<MatchResult>> byAlgorithm)
            throws IOException {
        boolean newFile = !Files.exists(output);
        try (BufferedWriter writer =
                Files.newBufferedWriter(
                        output,
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND)) {
            if (newFile) {
                writeCsvRow(
                        writer,
                        List.of(
                                "escenario",
                                "algoritmo",
                                "n",
                                "m",
                                "sigma",
                                "muestras",
                                "medianaNanosPreproceso",
                                "q1NanosPreproceso",
                                "q3NanosPreproceso",
                                "iqrNanosPreproceso",
                                "medianaNanosBusqueda",
                                "q1NanosBusqueda",
                                "q3NanosBusqueda",
                                "iqrNanosBusqueda",
                                "medianaComparaciones",
                                "q1Comparaciones",
                                "q3Comparaciones",
                                "iqrComparaciones"));
            }
            for (Algorithm algorithm : Algorithm.values()) {
                List<MatchResult> results = byAlgorithm.get(algorithm);
                writeCsvRow(
                        writer,
                        List.of(
                                benchmarkCase.scenario(),
                                algorithm.csvName,
                                Integer.toString(benchmarkCase.n()),
                                Integer.toString(benchmarkCase.m()),
                                Integer.toString(benchmarkCase.sigma()),
                                Integer.toString(results.size()),
                                stats(results, Metric.PREPROCESS, 0.5),
                                stats(results, Metric.PREPROCESS, 0.25),
                                stats(results, Metric.PREPROCESS, 0.75),
                                iqr(results, Metric.PREPROCESS),
                                stats(results, Metric.SEARCH, 0.5),
                                stats(results, Metric.SEARCH, 0.25),
                                stats(results, Metric.SEARCH, 0.75),
                                iqr(results, Metric.SEARCH),
                                stats(results, Metric.COMPARISONS, 0.5),
                                stats(results, Metric.COMPARISONS, 0.25),
                                stats(results, Metric.COMPARISONS, 0.75),
                                iqr(results, Metric.COMPARISONS)));
            }
        }
    }

    private static String iqr(List<MatchResult> results, Metric metric) {
        return Double.toString(quantile(results, metric, 0.75) - quantile(results, metric, 0.25));
    }

    private static String stats(List<MatchResult> results, Metric metric, double quantile) {
        return Double.toString(quantile(results, metric, quantile));
    }

    private static double quantile(List<MatchResult> results, Metric metric, double probability) {
        List<Long> values = results.stream().map(result -> metric.value(result)).sorted().toList();
        double position = probability * (values.size() - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        return values.get(lower) + (values.get(upper) - values.get(lower)) * (position - lower);
    }

    private static void writeCsvRow(BufferedWriter writer, List<String> values) throws IOException {
        writer.write(
                values.stream()
                        .map(BenchmarkRunner::escapeCsv)
                        .collect(java.util.stream.Collectors.joining(",")));
        writer.newLine();
    }

    private static String escapeCsv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static Path parseConfigPath(String[] args) {
        if (args.length != 2 || !"--config".equals(args[0])) {
            throw new IllegalArgumentException("Uso: BenchmarkRunner --config <archivo.json>");
        }
        return Path.of(args[1]);
    }

    private static Path resolve(Path base, Path path) {
        return path.isAbsolute() ? path : base.resolve(path).normalize();
    }

    private static void createParentDirectory(Path output) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private enum Metric {
        PREPROCESS {
            @Override
            long value(MatchResult result) {
                return result.nanosPreproceso();
            }
        },
        SEARCH {
            @Override
            long value(MatchResult result) {
                return result.nanosBusqueda();
            }
        },
        COMPARISONS {
            @Override
            long value(MatchResult result) {
                return result.comparaciones();
            }
        };

        abstract long value(MatchResult result);
    }

    private enum Algorithm {
        KMP("KMP", new KmpMatcher()),
        BOYER_MOORE("Boyer-Moore", new BoyerMooreMatcher()),
        RABIN_KARP("Rabin-Karp", new RabinKarpMatcher());

        private final String csvName;
        private final StringMatcher matcher;

        Algorithm(String csvName, StringMatcher matcher) {
            this.csvName = csvName;
            this.matcher = matcher;
        }

        StringMatcher matcher() {
            return matcher;
        }
    }
}
