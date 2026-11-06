package com.bibliometria.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfIngestorServiceTest {

    @TempDir Path temporaryDirectory;

    private Path rawDirectory;
    private Path manifestPath;

    @BeforeEach
    void setUp() throws IOException {
        Path projectRoot = findProjectRoot();
        Path dataDirectory =
                Files.createDirectories(
                        temporaryDirectory.resolve(projectRoot.getFileName()).resolve("data"));
        rawDirectory = Files.createDirectories(dataDirectory.resolve("raw"));
        manifestPath = dataDirectory.resolve("manifest.csv");

        StringBuilder manifest = new StringBuilder("id,filename,sha256\n");
        for (int index = 1; index <= 20; index++) {
            String id = "A%02d".formatted(index);
            String filename = "article-%02d.pdf".formatted(index);
            Path pdfPath = rawDirectory.resolve(filename);
            createSyntheticPdf(pdfPath, id);
            manifest.append(id)
                    .append(',')
                    .append(filename)
                    .append(',')
                    .append(sha256(pdfPath))
                    .append('\n');
        }
        Files.writeString(manifestPath, manifest, StandardCharsets.UTF_8);
    }

    @Test
    void ingestsTheSyntheticManifestAndWritesTwentyRecords() throws IOException {
        Path output = temporaryDirectory.resolve("corpus.json");

        var records = new PdfIngestorService().ingest(rawDirectory, manifestPath, output);

        assertEquals(20, records.size());
        assertTrueCorpusShape(output);
    }

    @Test
    void preservesManualApprovalWhenTheSourcePdfIsUnchanged() throws IOException {
        Path output = temporaryDirectory.resolve("corpus.json");
        PdfIngestorService service = new PdfIngestorService();
        List<PdfIngestorService.CorpusRecord> firstRun =
                service.ingest(rawDirectory, manifestPath, output);
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
                service.ingest(rawDirectory, manifestPath, output);

        assertTrue(secondRun.getFirst().verified());
        assertEquals("Human-verified title", secondRun.getFirst().title());
        assertEquals("Jhoan Urrea; Tatiana", secondRun.getFirst().verificadoPor());
        assertEquals(List.of("Double review completed"), secondRun.getFirst().notas());
    }

    private Path findProjectRoot() throws IOException {
        Path start = Path.of("").toAbsolutePath().normalize();
        Path current = start;
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("core"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IOException("No se encontró la raíz del proyecto desde: " + start);
    }

    private void createSyntheticPdf(Path pdfPath, String id) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDDocumentInformation information = document.getDocumentInformation();
            information.setTitle("Synthetic Bibliometric Article " + id);
            information.setAuthor("Ada Lovelace; Alan Turing");
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                content.newLineAtOffset(50, 750);
                content.showText("ABSTRACT");
                content.newLineAtOffset(0, -20);
                content.showText(
                        "This synthetic abstract provides repeatable content for testing PDF "
                                + "ingestion without external documents.");
                content.newLineAtOffset(0, -20);
                content.showText(
                        "It includes enough descriptive text to validate extraction and corpus "
                                + "serialization in continuous integration.");
                content.newLineAtOffset(0, -20);
                content.showText("INTRODUCTION");
                content.endText();
            }

            document.save(pdfPath.toFile());
        }
    }

    private String sha256(Path path) throws IOException {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
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
