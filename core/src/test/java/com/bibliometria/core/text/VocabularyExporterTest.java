package com.bibliometria.core.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VocabularyExporterTest {

    @TempDir Path tempDirectory;

    @Test
    void exportsDeterministicVocabularyTextsAndCorpusHash() throws Exception {
        String corpus =
                """
        [
          {"id":"A1","title":"Café \\"AI\\"","authors":"Ignored author","abstractText":"Artificial intelligence","verified":true},
          {"id":"A2","title":"AI","authors":"Another ignored author","abstractText":"café"}
        ]
        """;
        Path corpusPath = tempDirectory.resolve("corpus.json");
        Path outputDirectory = tempDirectory.resolve("output");
        Files.writeString(corpusPath, corpus, StandardCharsets.UTF_8);

        String actualHash = new VocabularyExporter().exportar(corpusPath, outputDirectory);

        assertThat(actualHash).isEqualTo(sha256(corpus));
        assertThat(
                        Files.readString(
                                outputDirectory.resolve("vocabulario.txt"), StandardCharsets.UTF_8))
                .isEqualTo("ai\nartificial\ncafé\nintelligence\n");
        assertThat(
                        Files.readString(
                                outputDirectory.resolve("textos_p_texto.json"),
                                StandardCharsets.UTF_8))
                .contains("\"corpusSha256\":\"" + actualHash + "\"")
                .contains(
                        "\"id\":\"A1\",\"textoPTexto\":\"Café \\\"AI\\\" Artificial intelligence\"")
                .contains("\"id\":\"A2\",\"textoPTexto\":\"AI café\"")
                .doesNotContain("Ignored author");
    }

    @Test
    void rejectsArticlesWithMissingRequiredFields() throws IOException {
        Path corpusPath = tempDirectory.resolve("invalid-corpus.json");
        Files.writeString(corpusPath, "[{\"id\":\"A1\",\"title\":\"Missing abstract\"}]");

        assertThatThrownBy(
                        () ->
                                new VocabularyExporter()
                                        .exportar(corpusPath, tempDirectory.resolve("output")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("abstractText");
    }

    @Test
    void rejectsMalformedJson() throws IOException {
        Path corpusPath = tempDirectory.resolve("malformed-corpus.json");
        Files.writeString(corpusPath, "[{\"id\":\"A1\",}]");

        assertThatThrownBy(
                        () ->
                                new VocabularyExporter()
                                        .exportar(corpusPath, tempDirectory.resolve("output")))
                .isInstanceOf(IOException.class);
    }

    private static String sha256(String value) throws NoSuchAlgorithmException {
        return HexFormat.of()
                .formatHex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
