package com.bibliometria.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/** Genera los conjuntos de entrada E1 (real), E2 (sintético) y E3 (adversarial). */
public final class ScenarioGenerator {

    private static final char FIRST_PRINTABLE_CHAR = '!';
    private static final int PRINTABLE_ALPHABET_SIZE = 94;
    private static final String E3_SYMBOL = "a";

    private ScenarioGenerator() {}

    /**
     * Construye todos los casos solicitados en el orden de configuración.
     *
     * @param config configuración del runner
     * @param configDirectory carpeta usada para resolver rutas relativas
     * @param objectMapper lector JSON del corpus E1
     * @return casos listos para ejecutar
     * @throws IOException cuando el corpus real E1 no se puede leer
     */
    public static List<BenchmarkCase> generate(
            BenchmarkConfig config, Path configDirectory, ObjectMapper objectMapper)
            throws IOException {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(configDirectory, "configDirectory");
        Objects.requireNonNull(objectMapper, "objectMapper");
        List<BenchmarkCase> cases = new ArrayList<>();
        String corpusText = null;
        for (int index = 0; index < config.scenarios().size(); index++) {
            BenchmarkConfig.ScenarioSpec scenario = config.scenarios().get(index);
            switch (scenario.type()) {
                case E1 -> {
                    if (corpusText == null) {
                        corpusText = loadAbstracts(config, configDirectory, objectMapper);
                    }
                    cases.addAll(generateE1(scenario, corpusText));
                }
                case E2 -> cases.addAll(generateE2(scenario, config.seed() + index));
                case E3 -> cases.addAll(generateE3(scenario));
            }
        }
        return List.copyOf(cases);
    }

    private static List<BenchmarkCase> generateE1(
            BenchmarkConfig.ScenarioSpec scenario, String abstracts) {
        String present = requirePattern(scenario.presentPattern(), "presentPattern");
        String absent = requirePattern(scenario.absentPattern(), "absentPattern");
        if (!abstracts.contains(present)) {
            throw new IllegalArgumentException(
                    "El patrón E1 presentPattern no aparece en los abstracts concatenados.");
        }
        if (abstracts.contains(absent)) {
            throw new IllegalArgumentException(
                    "El patrón E1 absentPattern sí aparece en los abstracts concatenados.");
        }
        int sigma = alphabetSize(abstracts);
        return List.of(
                new BenchmarkCase("E1-present", abstracts, present, sigma),
                new BenchmarkCase("E1-absent", abstracts, absent, sigma));
    }

    private static List<BenchmarkCase> generateE2(
            BenchmarkConfig.ScenarioSpec scenario, long seed) {
        requireNonEmpty(scenario.textSizes(), "E2 textSizes");
        requireNonEmpty(scenario.patternLengths(), "E2 patternLengths");
        requireNonEmpty(scenario.alphabetSizes(), "E2 alphabetSizes");
        List<BenchmarkCase> cases = new ArrayList<>();
        long caseIndex = 0;
        for (int n : scenario.textSizes()) {
            for (int m : scenario.patternLengths()) {
                validateLengths(n, m);
                for (int sigma : scenario.alphabetSizes()) {
                    if (sigma < 1 || sigma > PRINTABLE_ALPHABET_SIZE) {
                        throw new IllegalArgumentException(
                                "E2 alphabetSizes debe estar entre 1 y "
                                        + PRINTABLE_ALPHABET_SIZE
                                        + ".");
                    }
                    Random random = new Random(seed + caseIndex++);
                    String alphabet = printableAlphabet(sigma);
                    String text = randomText(n, alphabet, random);
                    String pattern = randomText(m, alphabet, random);
                    cases.add(new BenchmarkCase("E2", text, pattern, sigma));
                }
            }
        }
        return List.copyOf(cases);
    }

    private static List<BenchmarkCase> generateE3(BenchmarkConfig.ScenarioSpec scenario) {
        requireNonEmpty(scenario.textSizes(), "E3 textSizes");
        requireNonEmpty(scenario.patternLengths(), "E3 patternLengths");
        List<BenchmarkCase> cases = new ArrayList<>();
        for (int n : scenario.textSizes()) {
            for (int m : scenario.patternLengths()) {
                validateLengths(n, m);
                cases.add(new BenchmarkCase("E3", E3_SYMBOL.repeat(n), E3_SYMBOL.repeat(m), 1));
            }
        }
        return List.copyOf(cases);
    }

    private static String loadAbstracts(
            BenchmarkConfig config, Path configDirectory, ObjectMapper objectMapper)
            throws IOException {
        if (config.corpus() == null) {
            throw new IllegalArgumentException(
                    "E1 requiere la propiedad corpus en la configuración.");
        }
        Path corpusPath =
                config.corpus().isAbsolute()
                        ? config.corpus()
                        : configDirectory.resolve(config.corpus());
        JsonNode corpus = objectMapper.readTree(corpusPath.toFile());
        if (!corpus.isArray() || corpus.isEmpty()) {
            throw new IllegalArgumentException("El corpus E1 debe contener abstracts no vacíos.");
        }
        List<String> abstracts = new ArrayList<>();
        for (JsonNode article : corpus) {
            JsonNode abstractNode = article.get("abstractText");
            if (abstractNode == null
                    || !abstractNode.isTextual()
                    || abstractNode.asText().isBlank()) {
                throw new IllegalArgumentException(
                        "Cada artículo del corpus E1 debe contener un abstractText no vacío.");
            }
            abstracts.add(abstractNode.asText());
        }
        return String.join("\n", abstracts);
    }

    private static String printableAlphabet(int sigma) {
        return new String(
                java.util.stream.IntStream.range(0, sigma)
                        .map(offset -> FIRST_PRINTABLE_CHAR + offset)
                        .collect(
                                StringBuilder::new,
                                StringBuilder::appendCodePoint,
                                StringBuilder::append)
                        .toString());
    }

    private static String randomText(int length, String alphabet, Random random) {
        StringBuilder text = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            text.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return text.toString();
    }

    private static int alphabetSize(String text) {
        Set<Character> alphabet = new LinkedHashSet<>();
        for (int index = 0; index < text.length(); index++) {
            alphabet.add(text.charAt(index));
        }
        return alphabet.size();
    }

    private static String requirePattern(String pattern, String property) {
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("E1 requiere " + property + " no vacío.");
        }
        return pattern;
    }

    private static void validateLengths(int n, int m) {
        if (n < 1 || m < 1 || m > n) {
            throw new IllegalArgumentException("Se requiere 1 <= m <= n.");
        }
    }

    private static void requireNonEmpty(List<Integer> values, String property) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException(property + " no puede estar vacío.");
        }
    }
}
