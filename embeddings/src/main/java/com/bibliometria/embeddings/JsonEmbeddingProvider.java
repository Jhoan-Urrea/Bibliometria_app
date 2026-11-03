package com.bibliometria.embeddings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Carga y valida los vectores IA-1 e IA-2 generados por el proceso de embeddings. */
public final class JsonEmbeddingProvider {

    private static final String IA1_FILE = "vectors_ia1.json";
    private static final String IA2_FILE = "embeddings_ia2.json";
    private static final String METADATA_FILE = "embeddings.meta.json";
    private static final int IA1_DIMENSIONS = 300;
    private static final int IA2_DIMENSIONS = 384;
    private static final double IA2_NORM_TOLERANCE = 1e-6;

    private final ObjectMapper objectMapper;

    public JsonEmbeddingProvider() {
        this(new ObjectMapper());
    }

    JsonEmbeddingProvider(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * Carga ambos conjuntos y comprueba su integridad contra los hashes del archivo de metadatos.
     *
     * @param directory directorio que contiene los dos archivos JSON y sus metadatos
     * @return mapa inmutable cuyos identificadores de modelo son {@code IA-1} e {@code IA-2}
     * @throws IOException si falta un archivo o si sus datos no cumplen el contrato
     */
    public Map<String, Map<String, double[]>> load(Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory");
        Path metadataPath = directory.resolve(METADATA_FILE);
        JsonNode metadata = readJson(metadataPath);
        JsonNode outputs = metadata.path("outputs");

        Path ia1Path = directory.resolve(IA1_FILE);
        Path ia2Path = directory.resolve(IA2_FILE);
        verifySha256(ia1Path, outputs.path(IA1_FILE));
        verifySha256(ia2Path, outputs.path(IA2_FILE));

        Map<String, double[]> ia1 = readEmbeddings(ia1Path, IA1_DIMENSIONS, false);
        Map<String, double[]> ia2 = readEmbeddings(ia2Path, IA2_DIMENSIONS, true);
        return Map.of("IA-1", Map.copyOf(ia1), "IA-2", Map.copyOf(ia2));
    }

    private JsonNode readJson(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Required embeddings file does not exist: " + path);
        }
        JsonNode json = objectMapper.readTree(path.toFile());
        if (json == null) {
            throw new IOException("JSON file is empty: " + path);
        }
        return json;
    }

    private void verifySha256(Path path, JsonNode expectedNode) throws IOException {
        if (!expectedNode.isTextual()) {
            throw new IOException(
                    "Missing SHA-256 for " + path.getFileName() + " in " + METADATA_FILE);
        }
        String expected = expectedNode.textValue();
        if (!expected.matches("[0-9a-fA-F]{64}")) {
            throw new IOException(
                    "Invalid SHA-256 for " + path.getFileName() + " in " + METADATA_FILE);
        }
        String actual = sha256(path);
        if (!actual.equalsIgnoreCase(expected)) {
            throw new IOException("SHA-256 mismatch for " + path.getFileName());
        }
    }

    private Map<String, double[]> readEmbeddings(Path path, int dimensions, boolean unitNorm)
            throws IOException {
        JsonNode root = readJson(path);
        if (!root.isArray()) {
            throw new IOException(path.getFileName() + " must contain a JSON array.");
        }
        Map<String, double[]> embeddings = new LinkedHashMap<>();
        for (JsonNode entry : root) {
            JsonNode idNode = entry.path("id");
            JsonNode vectorNode = entry.path("vector");
            if (!idNode.isTextual() || idNode.textValue().isBlank()) {
                throw new IOException(
                        "Every embedding must have a non-empty id in " + path.getFileName());
            }
            String id = idNode.textValue();
            if (!vectorNode.isArray() || vectorNode.size() != dimensions) {
                throw new IOException(
                        "Embedding "
                                + id
                                + " in "
                                + path.getFileName()
                                + " must have "
                                + dimensions
                                + " dimensions.");
            }

            double[] vector = new double[dimensions];
            double norm = 0.0;
            for (int index = 0; index < dimensions; index++) {
                JsonNode valueNode = vectorNode.get(index);
                if (!valueNode.isNumber()) {
                    throw new IOException(
                            "Embedding " + id + " contains a non-numeric or non-finite value.");
                }
                double value = valueNode.doubleValue();
                if (!Double.isFinite(value)) {
                    throw new IOException(
                            "Embedding " + id + " contains a non-numeric or non-finite value.");
                }
                vector[index] = value;
                norm = Math.hypot(norm, value);
            }
            if (unitNorm && Math.abs(norm - 1.0) > IA2_NORM_TOLERANCE) {
                throw new IOException(
                        "Embedding "
                                + id
                                + " in "
                                + path.getFileName()
                                + " must have unit L2 norm.");
            }
            if (embeddings.putIfAbsent(id, vector) != null) {
                throw new IOException("Duplicate embedding id " + id + " in " + path.getFileName());
            }
        }
        return embeddings;
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available in this JDK.", exception);
        }
    }
}
