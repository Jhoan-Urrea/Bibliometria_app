package com.bibliometria.core.clustering;

import com.bibliometria.core.similarity.SimilarityResult;
import com.bibliometria.core.similarity.TfIdfSimilarity;
import com.bibliometria.core.text.ProcessedDocument;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Inmutable, determinista matriz de distancias TF-IDF para el corpus de referencia.
 *
 * <p>Correspondencia fórmula-código: {@code delta(i,j) = 1 - simCos(i,j)}; la similitud procede de
 * {@link TfIdfSimilarity} y su valor normalizado. Se calcula cada par una sola vez y se copia en
 * ambas posiciones para garantizar simetría exacta. La diagonal se fija estrictamente en cero. Los
 * resultados numéricamente fuera de {@code [0,1]} hasta {@value #TOLERANCE} se ajustan al límite;
 * valores no finitos o fuera de esa tolerancia se rechazan.
 */
public final class DistanceMatrix {

    private static final double TOLERANCE = 1e-12;

    private final List<String> documentIds;
    private final Map<String, Integer> indexById;
    private final double[][] distances;

    /**
     * Construye la matriz completa usando el modelo TF-IDF existente.
     *
     * <p>{@link TfIdfSimilarity} valida que la entrada tenga exactamente los veinte documentos
     * únicos requeridos por el modelo global. Las filas y columnas se ordenan por ID ascendente,
     * independientemente del orden de entrada.
     *
     * @param corpus los documentos procesados que definen el universo de referencia
     * @throws NullPointerException si la lista o alguno de sus documentos es null
     * @throws IllegalArgumentException si el corpus no cumple los invariantes TF-IDF o contiene IDs
     *     en blanco
     */
    public DistanceMatrix(List<ProcessedDocument> corpus) {
        Objects.requireNonNull(corpus, "corpus");
        List<ProcessedDocument> orderedDocuments = new ArrayList<>(corpus);
        orderedDocuments.sort(Comparator.comparing(ProcessedDocument::id));

        TfIdfSimilarity tfIdf = new TfIdfSimilarity(orderedDocuments);
        List<String> ids = new ArrayList<>(orderedDocuments.size());
        Map<String, Integer> indices = new HashMap<>();
        for (int index = 0; index < orderedDocuments.size(); index++) {
            String id = orderedDocuments.get(index).id();
            if (id.isBlank()) {
                throw new IllegalArgumentException("Document IDs must not be blank.");
            }
            ids.add(id);
            indices.put(id, index);
        }

        documentIds = List.copyOf(ids);
        indexById = Map.copyOf(indices);
        distances = new double[orderedDocuments.size()][orderedDocuments.size()];
        for (int row = 0; row < orderedDocuments.size(); row++) {
            distances[row][row] = 0.0;
            for (int column = row + 1; column < orderedDocuments.size(); column++) {
                SimilarityResult similarity =
                        tfIdf.calcular(orderedDocuments.get(row), orderedDocuments.get(column));
                double distance = normalizeDistance(1.0 - similarity.normalizado());
                distances[row][column] = distance;
                distances[column][row] = distance;
            }
        }
        validar();
    }

    /**
     * Creates a validated matrix from precomputed distances, useful for clustering and reproducible
     * fixtures.
     *
     * <p>IDs are sorted canonically and the matrix is reordered to match. Distances must be finite,
     * symmetric within {@value #TOLERANCE}, in {@code [0,1]} within tolerance, and have an exactly
     * zero diagonal. Accepted floating-point noise is clamped at the bounds and symmetric entries
     * are averaged before storage.
     *
     * @param documentIds IDs corresponding to the input matrix rows and columns
     * @param values square distance matrix in the same order as {@code documentIds}
     * @return an immutable, canonically ordered distance matrix
     */
    public static DistanceMatrix fromDistances(List<String> documentIds, double[][] values) {
        Objects.requireNonNull(documentIds, "documentIds");
        Objects.requireNonNull(values, "values");
        if (documentIds.isEmpty() || values.length != documentIds.size()) {
            throw new IllegalArgumentException(
                    "Distance matrix dimensions must match non-empty IDs.");
        }

        List<Integer> order = new ArrayList<>(documentIds.size());
        Map<String, Integer> originalIndices = new HashMap<>();
        for (int index = 0; index < documentIds.size(); index++) {
            String id = Objects.requireNonNull(documentIds.get(index), "document ID");
            if (id.isBlank() || originalIndices.putIfAbsent(id, index) != null) {
                throw new IllegalArgumentException("Document IDs must be non-blank and unique.");
            }
            order.add(index);
        }
        order.sort(Comparator.comparing(documentIds::get));

        double[][] canonical = new double[values.length][values.length];
        List<String> sortedIds = order.stream().map(documentIds::get).toList();
        for (int row = 0; row < values.length; row++) {
            if (values[row] == null || values[row].length != values.length) {
                throw new IllegalArgumentException("Distance matrix must be square.");
            }
        }
        for (int row = 0; row < values.length; row++) {
            int originalRow = order.get(row);
            for (int column = row; column < values.length; column++) {
                int originalColumn = order.get(column);
                double distance = values[originalRow][originalColumn];
                double reverse = values[originalColumn][originalRow];
                if (!Double.isFinite(distance) || !Double.isFinite(reverse)) {
                    throw new IllegalArgumentException("Distances must be finite.");
                }
                if (row == column) {
                    if (distance != 0.0) {
                        throw new IllegalArgumentException(
                                "Distance matrix diagonal must be exactly zero.");
                    }
                    canonical[row][column] = 0.0;
                    continue;
                }
                if (distance < -TOLERANCE
                        || distance > 1.0 + TOLERANCE
                        || reverse < -TOLERANCE
                        || reverse > 1.0 + TOLERANCE) {
                    throw new IllegalArgumentException("Distances must be in [0, 1].");
                }
                if (Math.abs(distance - reverse) > TOLERANCE) {
                    throw new IllegalArgumentException("Distance matrix must be symmetric.");
                }
                double normalized = Math.max(0.0, Math.min(1.0, (distance + reverse) / 2.0));
                canonical[row][column] = normalized;
                canonical[column][row] = normalized;
            }
        }
        return new DistanceMatrix(sortedIds, canonical);
    }

    private DistanceMatrix(List<String> documentIds, double[][] distances) {
        this.documentIds = List.copyOf(documentIds);
        Map<String, Integer> indices = new HashMap<>();
        for (int index = 0; index < documentIds.size(); index++) {
            indices.put(documentIds.get(index), index);
        }
        this.indexById = Map.copyOf(indices);
        this.distances = new double[distances.length][];
        for (int row = 0; row < distances.length; row++) {
            this.distances[row] = distances[row].clone();
        }
        validar();
    }

    /** Returns the number of documents represented by this matrix. */
    public int size() {
        return documentIds.size();
    }

    /** Returns immutable document IDs in canonical ascending order. */
    public List<String> documentIds() {
        return documentIds;
    }

    /**
     * Returns the distance at the given row and column.
     *
     * @throws IndexOutOfBoundsException if either index is outside the matrix
     */
    public double distance(int row, int column) {
        return distances[row][column];
    }

    /**
     * Returns the distance between two corpus IDs.
     *
     * @throws IllegalArgumentException if either ID does not belong to this matrix
     */
    public double distance(String documentIdA, String documentIdB) {
        Integer row = indexById.get(Objects.requireNonNull(documentIdA, "documentIdA"));
        Integer column = indexById.get(Objects.requireNonNull(documentIdB, "documentIdB"));
        if (row == null || column == null) {
            throw new IllegalArgumentException("Both document IDs must exist in the matrix.");
        }
        return distances[row][column];
    }

    /**
     * Returns a defensive copy of the complete matrix.
     *
     * @return a deep copy; modifying it cannot mutate this instance
     */
    public double[][] toArray() {
        double[][] copy = new double[distances.length][];
        for (int row = 0; row < distances.length; row++) {
            copy[row] = distances[row].clone();
        }
        return copy;
    }

    /**
     * Validates symmetry, an exactly-zero diagonal, finiteness, and the distance bounds.
     *
     * <p>The constructor calls this method after calculation. Since internal state is never
     * exposed, callers may also use it as an explicit integrity assertion.
     *
     * @throws IllegalStateException if any matrix invariant is violated
     */
    public void validar() {
        if (distances.length != documentIds.size()) {
            throw new IllegalStateException("Distance matrix must be square and match its IDs.");
        }
        for (int row = 0; row < distances.length; row++) {
            if (distances[row].length != distances.length) {
                throw new IllegalStateException("Distance matrix must be square.");
            }
            if (distances[row][row] != 0.0) {
                throw new IllegalStateException("Distance matrix diagonal must be exactly zero.");
            }
            for (int column = 0; column < distances.length; column++) {
                double distance = distances[row][column];
                if (!Double.isFinite(distance)) {
                    throw new IllegalStateException("Distances must be finite.");
                }
                if (distance < -TOLERANCE || distance > 1.0 + TOLERANCE) {
                    throw new IllegalStateException("Distances must be in [0, 1].");
                }
                if (Math.abs(distance - distances[column][row]) > TOLERANCE) {
                    throw new IllegalStateException("Distance matrix must be symmetric.");
                }
            }
        }
    }

    private static double normalizeDistance(double distance) {
        if (!Double.isFinite(distance)) {
            throw new IllegalArgumentException("Calculated distance must be finite.");
        }
        if (distance < -TOLERANCE || distance > 1.0 + TOLERANCE) {
            throw new IllegalArgumentException("Calculated distance is outside [0, 1].");
        }
        return Math.max(0.0, Math.min(1.0, distance));
    }
}
