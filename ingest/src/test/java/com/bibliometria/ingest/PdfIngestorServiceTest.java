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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    @Test
    void preservesManualApprovalWhenTheSourcePdfIsUnchanged() throws IOException {
        Path projectRoot = findProjectRoot();
        Path output = temporaryDirectory.resolve("corpus.json");
        PdfIngestorService service = new PdfIngestorService();
        List<PdfIngestorService.CorpusRecord> firstRun =
                service.ingest(
                        projectRoot.resolve("data/raw"),
                        projectRoot.resolve("data/manifest.csv"),
                        output);
        PdfIngestorService.CorpusRecord original = firstRun.getFirst();
        PdfIngestorService.CorpusRecord approved =
                new PdfIngestorService.CorpusRecord(
                        original.id(),
                        original.filename(),
                        original.sha256(),
                        "Human-verified title",
                        original.authors(),
                        original.abstractText(),
                        original.language(),
                        original.paginaAbstract(),
                        true,
                        "Jhoan Urrea; Tatiana",
                        List.of("Double review completed"));
        List<PdfIngestorService.CorpusRecord> seeded = new ArrayList<>(firstRun);
        seeded.set(0, approved);
        new ObjectMapper().writeValue(output.toFile(), seeded);

        List<PdfIngestorService.CorpusRecord> secondRun =
                service.ingest(
                        projectRoot.resolve("data/raw"),
                        projectRoot.resolve("data/manifest.csv"),
                        output);

        assertTrue(secondRun.getFirst().verified());
        assertEquals("Human-verified title", secondRun.getFirst().title());
        assertEquals("Jhoan Urrea; Tatiana", secondRun.getFirst().verificadoPor());
        assertEquals(List.of("Double review completed"), secondRun.getFirst().notas());
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
            assertEquals(
                    Set.of(
                            "id",
                            "filename",
                            "sha256",
                            "title",
                            "authors",
                            "abstractText",
                            "language",
                            "paginaAbstract",
                            "verified",
                            "verificadoPor",
                            "notas"),
                    fields);
            assertTrue(record.get("id").isTextual());
            assertTrue(record.get("filename").isTextual());
            assertTrue(record.get("sha256").asText().matches("[0-9a-f]{64}"));
            assertTrue(record.get("title").isTextual());
            assertFalse(record.get("title").asText().isBlank());
            assertTrue(record.get("authors").isTextual());
            assertFalse(record.get("authors").asText().isBlank());
            assertTrue(record.get("abstractText").isTextual());
            assertTrue(record.get("abstractText").asText().length() >= 100);
            assertEquals("en", record.get("language").asText());
            assertTrue(record.get("paginaAbstract").isInt());
            assertNotNull(record.get("verified"));
            assertTrue(record.get("verified").isBoolean());
            assertFalse(record.get("verified").asBoolean());
            assertEquals("PENDIENTE_REVISION_HUMANA", record.get("verificadoPor").asText());
            assertTrue(record.get("notas").isArray());
        }
    }
}
