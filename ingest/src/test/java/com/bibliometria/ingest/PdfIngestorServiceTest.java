package com.bibliometria.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfIngestorServiceTest {

    @TempDir Path temporaryDirectory;

    @Test
    void ingestsTheLocalManifestAndWritesTwentyRecords() throws IOException {
        Path projectRoot = findProjectRoot();
        Path output = temporaryDirectory.resolve("corpus.json");

        var records =
                new PdfIngestorService()
                        .ingest(
                                projectRoot.resolve("data/raw"),
                                projectRoot.resolve("data/manifest.csv"),
                                output);

        assertEquals(20, records.size());
        assertTrueCorpusShape(output);
    }

    private Path findProjectRoot() throws IOException {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("data/manifest.csv"))
                    && Files.isDirectory(current.resolve("data/raw"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IOException("No se encontró data/manifest.csv desde el directorio de trabajo");
    }

    private void assertTrueCorpusShape(Path output) throws IOException {
        JsonNode corpus = new ObjectMapper().readTree(output.toFile());
        assertEquals(20, corpus.size());
        for (JsonNode record : corpus) {
            Set<String> fields = new HashSet<>();
            record.fieldNames().forEachRemaining(fields::add);
            assertEquals(Set.of("id", "title", "authors", "abstractText", "verified"), fields);
            assertTrue(record.get("id").isTextual());
            assertTrue(record.get("title").isTextual());
            assertTrue(record.get("authors").isTextual());
            assertTrue(record.get("abstractText").isTextual());
            assertNotNull(record.get("verified"));
            assertTrue(record.get("verified").isBoolean());
            assertFalse(record.get("verified").asBoolean());
        }
    }
}
