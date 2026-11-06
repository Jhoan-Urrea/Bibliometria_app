package com.bibliometria.embeddings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonEmbeddingProviderTest {

    @TempDir Path directory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Path ia1Path;
    private Path ia2Path;

    @BeforeEach
    void setUp() throws IOException {
        ia1Path = directory.resolve("vectors_ia1.json");
        ia2Path = directory.resolve("embeddings_ia2.json");
        writeEmbeddings(ia1Path, vector(300, 0.0));
        writeEmbeddings(ia2Path, unitVector());
        writeMetadata();
    }

    @Test
    void loadsBothEmbeddingSetsAfterVerifyingHashesAndVectors() throws IOException {
        Map<String, Map<String, double[]>> embeddings = new JsonEmbeddingProvider().load(directory);

        assertThat(embeddings).containsOnlyKeys("IA-1", "IA-2");
        assertThat(embeddings.get("IA-1").get("A1")).hasSize(300);
        assertThat(embeddings.get("IA-2").get("A1")).hasSize(384);
        assertThat(embeddings.get("IA-2").get("A1")[0]).isEqualTo(1.0);
    }

    @Test
    void rejectsFilesWhoseHashesDoNotMatchMetadata() throws IOException {
        writeEmbeddings(ia1Path, vector(300, 0.25));

        assertThatThrownBy(() -> new JsonEmbeddingProvider().load(directory))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("SHA-256 mismatch");
    }

    @Test
    void rejectsMissingEmbeddingMetadata() {
        assertThatThrownBy(() -> new JsonEmbeddingProvider().load(directory.resolve("missing")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Required embeddings file does not exist");
    }

    @Test
    void rejectsMalformedEmbeddingHashMetadata() throws IOException {
        objectMapper.writeValue(
                directory.resolve("embeddings.meta.json").toFile(),
                Map.of("outputs", Map.of("vectors_ia1.json", "not-a-sha256")));

        assertThatThrownBy(() -> new JsonEmbeddingProvider().load(directory))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Invalid SHA-256");
    }

    @Test
    void rejectsVectorsWithIncorrectDimensions() throws IOException {
        writeEmbeddings(ia1Path, vector(299, 0.0));
        writeMetadata();

        assertThatThrownBy(() -> new JsonEmbeddingProvider().load(directory))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("300 dimensions");
    }

    @Test
    void rejectsNonFiniteValues() throws IOException {
        List<Double> invalidVector = vector(300, 0.0);
        invalidVector.set(0, Double.NaN);
        writeEmbeddings(ia1Path, invalidVector);
        writeMetadata();

        assertThatThrownBy(() -> new JsonEmbeddingProvider().load(directory))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("non-numeric or non-finite");
    }

    @Test
    void rejectsIa2VectorsThatAreNotUnitNormalized() throws IOException {
        writeEmbeddings(ia2Path, vector(384, 0.0));
        writeMetadata();

        assertThatThrownBy(() -> new JsonEmbeddingProvider().load(directory))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("unit L2 norm");
    }

    private void writeMetadata() throws IOException {
        Map<String, Object> metadata =
                Map.of(
                        "outputs",
                        Map.of(
                                ia1Path.getFileName().toString(), sha256(ia1Path),
                                ia2Path.getFileName().toString(), sha256(ia2Path)));
        objectMapper.writeValue(directory.resolve("embeddings.meta.json").toFile(), metadata);
    }

    private void writeEmbeddings(Path path, List<Double> vector) throws IOException {
        objectMapper.writeValue(path.toFile(), List.of(Map.of("id", "A1", "vector", vector)));
    }

    private List<Double> unitVector() {
        List<Double> vector = vector(384, 0.0);
        vector.set(0, 1.0);
        return vector;
    }

    private List<Double> vector(int dimensions, double value) {
        List<Double> vector = new ArrayList<>(dimensions);
        for (int index = 0; index < dimensions; index++) {
            vector.add(value);
        }
        return vector;
    }

    private String sha256(Path path) throws IOException {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
