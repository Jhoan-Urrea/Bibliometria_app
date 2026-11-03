package com.bibliometria.core.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TextPreprocessorTest {

    @TempDir Path temporaryDirectory;

    @ParameterizedTest
    @MethodSource("normalizationCases")
    void normalizesBoundaryTextDeterministically(String input, String expected) throws IOException {
        TextPreprocessor preprocessor = newPreprocessor("");

        assertThat(preprocessor.normalizeText(input)).isEqualTo(expected);
    }

    private static Stream<Arguments> normalizationCases() {
        return Stream.of(
                Arguments.of("ﬁ", "fi"),
                Arguments.of("algo-\nritmos", "algoritmos"),
                Arguments.of("algo-\r\nritmos", "algoritmos"),
                Arguments.of("GPT-4", "GPT-4"),
                Arguments.of("fine-tuning", "fine-tuning"),
                Arguments.of("solo", "solo"),
                Arguments.of("", ""),
                Arguments.of(" \t\r\n ", ""),
                Arguments.of("42,007.5?! (A/B): #3", "42,007.5?! (A/B): #3"),
                Arguments.of("  texto\tcon\n espacios  ", "texto con espacios"),
                Arguments.of("A\u00a0B", "A B"),
                Arguments.of("Ñandú", "Ñandú"));
    }

    @Test
    void normalizesTokenizesFiltersAndCountsDeterministically() throws IOException {
        TextPreprocessor preprocessor = newPreprocessor("the\nand\n");
        Article article =
                new Article(
                        "A01",
                        "Ｔｈｅ arti-\nficial ＩＮＴＥＬＬＩＧＥＮＣＥ",
                        "Alice Example",
                        "AI, the future and AI!",
                        false);

        ProcessedDocument result = preprocessor.process(article);

        assertThat(result.textoNormalizado())
                .isEqualTo("The artificial INTELLIGENCE AI, the future and AI!");
        assertThat(result.tokens())
                .containsExactly("artificial", "intelligence", "ai", "future", "ai");
        assertThat(result.conjunto()).containsExactly("artificial", "intelligence", "ai", "future");
        assertThat(result.frecuencias())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("artificial", 1, "intelligence", 1, "ai", 2, "future", 1));
        assertThat(result.cadenaCanonica()).isEqualTo("artificial intelligence ai future ai");
    }

    @Test
    void preservesStopwordsInTextButFiltersTheirNormalizedTokens() throws IOException {
        TextPreprocessor preprocessor = newPreprocessor("the\n");

        ProcessedDocument result =
                preprocessor.process(new Article("A02", "", "", "The model and the data.", false));

        assertThat(result.textoNormalizado()).isEqualTo("The model and the data.");
        assertThat(result.tokens()).containsExactly("model", "and", "data");
    }

    @Test
    void retainsTheOnlyTokenInAnAbstract() throws IOException {
        ProcessedDocument result =
                newPreprocessor("").process(new Article("A03", "", "", "Unico", false));

        assertThat(result.tokens()).containsExactly("unico");
        assertThat(result.cadenaCanonica()).isEqualTo("unico");
    }

    @Test
    void punctuationAndInternalHyphensBecomeDeterministicTokenBoundaries() throws IOException {
        ProcessedDocument result =
                newPreprocessor("")
                        .process(new Article("A04", "", "", "GPT-4 fine-tuning 42,007.5?!", false));

        assertThat(result.tokens()).containsExactly("gpt", "4", "fine", "tuning", "42", "007", "5");
    }

    @Test
    void returnsNoTokensForAnEmptyOrWhitespaceOnlyAbstract() throws IOException {
        TextPreprocessor preprocessor = newPreprocessor("");

        assertThat(preprocessor.process(new Article("A05", "", "", "", false)).tokens()).isEmpty();
        assertThat(preprocessor.process(new Article("A06", "", "", " \t\r\n ", false)).tokens())
                .isEmpty();
    }

    @Test
    void producesIdenticalOutputAcrossRepeatedExecutions() throws IOException {
        TextPreprocessor preprocessor = newPreprocessor("the\n");
        Article article = new Article("A07", "ＧＰＴ-4", "", "fine-\ntuning in AI, 2025!", false);
        ProcessedDocument expected = preprocessor.process(article);
        String expectedNormalizedAbstract = preprocessor.normalizeText(article.abstractText());

        for (int execution = 0; execution < 100; execution++) {
            assertThat(preprocessor.process(article)).isEqualTo(expected);
            assertThat(preprocessor.normalizeText(article.abstractText()))
                    .isEqualTo(expectedNormalizedAbstract);
        }
    }

    @Test
    void processingNormalizedTextAgainIsIdempotent() throws IOException {
        TextPreprocessor preprocessor = newPreprocessor("and\n");
        Article original = new Article("A08", "Ｔhe arti-\nficial", "", "AI and data.", false);

        ProcessedDocument once = preprocessor.process(original);
        ProcessedDocument twice =
                preprocessor.process(new Article("A08", once.textoNormalizado(), "", "", false));

        assertThat(twice).isEqualTo(once);
    }

    @Test
    void everyAbstractInGeneratedCorpusProducesAtLeastOneToken() throws IOException {
        Path corpusPath = findGeneratedCorpus();
        List<String> abstracts = readAbstracts(corpusPath);

        assertThat(abstracts).as("abstractText del corpus generado en %s", corpusPath).hasSize(20);

        TextPreprocessor preprocessor = newPreprocessor("");
        for (int index = 0; index < abstracts.size(); index++) {
            assertThat(
                            preprocessor
                                    .process(
                                            new Article(
                                                    "corpus-" + index,
                                                    "",
                                                    "",
                                                    abstracts.get(index),
                                                    false))
                                    .tokens())
                    .as("Tok(d) del abstract del registro %s", index + 1)
                    .isNotEmpty();
        }
    }

    private TextPreprocessor newPreprocessor(String stopwords) throws IOException {
        Path stopwordsPath = temporaryDirectory.resolve("stopwords.txt");
        Files.writeString(stopwordsPath, stopwords, StandardCharsets.UTF_8);
        return new TextPreprocessor(stopwordsPath);
    }

    private Path findGeneratedCorpus() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path corpus = current.resolve("data/corpus.json");
            if (Files.isRegularFile(corpus)) {
                return corpus;
            }
            current = current.getParent();
        }
        return fail(
                "No se encontró data/corpus.json. Genérelo con PdfIngestorService según ingest/README.md");
    }

    private List<String> readAbstracts(Path corpusPath) throws IOException {
        List<String> abstracts = new ArrayList<>();
        try (Stream<String> lines = Files.lines(corpusPath, StandardCharsets.UTF_8)) {
            for (String line : lines.toList()) {
                String field = line.stripLeading();
                if (!field.startsWith("\"abstractText\"")) {
                    continue;
                }

                int valueStart = field.indexOf(':') + 1;
                while (valueStart < field.length()
                        && Character.isWhitespace(field.charAt(valueStart))) {
                    valueStart++;
                }
                if (valueStart == field.length() || field.charAt(valueStart) != '"') {
                    throw new IOException("abstractText no contiene una cadena JSON");
                }

                int stringStart = valueStart + 1;
                int stringEnd = findJsonStringEnd(field, stringStart);
                abstracts.add(decodeJsonString(field.substring(stringStart, stringEnd)));
            }
        }
        return abstracts;
    }

    private int findJsonStringEnd(String jsonLine, int stringStart) throws IOException {
        for (int index = stringStart; index < jsonLine.length(); index++) {
            if (jsonLine.charAt(index) == '\\') {
                index++;
            } else if (jsonLine.charAt(index) == '"') {
                return index;
            }
        }
        throw new IOException("Cadena JSON abstractText sin cerrar");
    }

    private String decodeJsonString(String encoded) {
        StringBuilder decoded = new StringBuilder(encoded.length());
        for (int index = 0; index < encoded.length(); index++) {
            char current = encoded.charAt(index);
            if (current != '\\') {
                decoded.append(current);
                continue;
            }
            char escape = encoded.charAt(++index);
            switch (escape) {
                case '"', '\\', '/' -> decoded.append(escape);
                case 'b' -> decoded.append('\b');
                case 'f' -> decoded.append('\f');
                case 'n' -> decoded.append('\n');
                case 'r' -> decoded.append('\r');
                case 't' -> decoded.append('\t');
                case 'u' -> {
                    decoded.append(
                            (char) Integer.parseInt(encoded.substring(index + 1, index + 5), 16));
                    index += 4;
                }
                default -> throw new IllegalArgumentException("Escape JSON no válido: \\" + escape);
            }
        }
        return decoded.toString();
    }
}
