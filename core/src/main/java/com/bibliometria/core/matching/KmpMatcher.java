package com.bibliometria.core.matching;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Búsqueda Knuth-Morris-Pratt con soporte para coincidencias solapadas.
 *
 * <p>Correspondencia fórmula-código (AR-F2-11):
 *
 * <table>
 *   <caption>Fórmulas KMP y código</caption>
 *   <tr><th>Fórmula / variable</th><th>Implementación Java</th></tr>
 *   <tr><td>{@code m = |P|}</td><td>{@code patron.length()} en {@link #buscarTodas}</td></tr>
 *   <tr><td>{@code n = |T|}</td><td>{@code texto.length()} en {@link #buscarTodas}</td></tr>
 *   <tr><td>{@code LPS[i] = max{k : 0 <= k <= i, P[0..k) = P[i-k+1..i]}}</td>
 *       <td>{@code lps[i]} en {@link #construirLps(String)}; prefijo propio más largo que es
 *       sufijo</td></tr>
 *   <tr><td>{@code T[i] == P[j]}</td><td>Comparación en {@link #buscarTodas}</td></tr>
 *   <tr><td>{@code j = LPS[j-1]}</td><td>Retorno tras desajuste o coincidencia en
 *       {@link #buscarTodas}</td></tr>
 * </table>
 *
 * <p>Complejidad: construir LPS cuesta {@code O(m)}; buscar cuesta {@code O(n)}; el total es {@code
 * O(n + m)}. El espacio auxiliar es {@code O(m)} para LPS; el almacenamiento de posiciones de
 * salida no se cuenta. La traza está desactivada por defecto.
 */
public final class KmpMatcher implements StringMatcher {

    private final TraceRecorder traceRecorder;

    public KmpMatcher() {
        this(TraceRecorder.sinTraza());
    }

    public KmpMatcher(TraceRecorder traceRecorder) {
        this.traceRecorder = Objects.requireNonNull(traceRecorder, "traceRecorder");
    }

    /**
     * Busca todas las posiciones de {@code P} en {@code T}, permitiendo coincidencias solapadas.
     *
     * <p>Usa {@code lps[j - 1]} para continuar tras coincidencias y desajustes sin retroceder en
     * {@code T}; las posiciones son índices UTF-16 de {@link String#charAt(int)}.
     *
     * @param texto texto {@code T} en el que buscar
     * @param patron patrón {@code P}
     * @return posiciones, tiempos y número de comparaciones de caracteres
     */
    @Override
    public MatchResult buscarTodas(String texto, String patron) {
        Objects.requireNonNull(texto, "texto");
        Objects.requireNonNull(patron, "patron");

        long inicioPreproceso = System.nanoTime();
        int[] lps = construirLps(patron);
        if (traceRecorder.habilitado()) {
            traceRecorder.registrar(
                    "KMP",
                    "tablaLPS",
                    Map.of("patron", patron, "lps", Arrays.stream(lps).boxed().toList()));
        }
        long nanosPreproceso = System.nanoTime() - inicioPreproceso;

        long inicioBusqueda = System.nanoTime();
        List<Integer> posiciones = new ArrayList<>();
        long comparaciones = 0;
        if (patron.isEmpty()) {
            for (int posicion = 0; posicion <= texto.length(); posicion++) {
                posiciones.add(posicion);
            }
        } else {
            int indiceTexto = 0;
            int indicePatron = 0;
            while (indiceTexto < texto.length()) {
                comparaciones++;
                if (texto.charAt(indiceTexto) == patron.charAt(indicePatron)) {
                    indiceTexto++;
                    indicePatron++;
                    if (indicePatron == patron.length()) {
                        posiciones.add(indiceTexto - indicePatron);
                        indicePatron = lps[indicePatron - 1];
                    }
                } else if (indicePatron > 0) {
                    indicePatron = lps[indicePatron - 1];
                } else {
                    indiceTexto++;
                }
            }
        }
        long nanosBusqueda = System.nanoTime() - inicioBusqueda;

        return new MatchResult(posiciones, nanosPreproceso, nanosBusqueda, comparaciones, Map.of());
    }

    /**
     * Construye en {@code O(m)} la tabla {@code lps[i]}, longitud del prefijo propio más largo de
     * {@code patron[0..i]} que también es sufijo.
     *
     * @param patron patrón {@code P}
     * @return tabla LPS de longitud {@code m}
     */
    public static int[] construirLps(String patron) {
        Objects.requireNonNull(patron, "patron");
        int[] lps = new int[patron.length()];
        int longitudPrefijo = 0;
        int indice = 1;
        while (indice < patron.length()) {
            if (patron.charAt(indice) == patron.charAt(longitudPrefijo)) {
                lps[indice] = ++longitudPrefijo;
                indice++;
            } else if (longitudPrefijo > 0) {
                longitudPrefijo = lps[longitudPrefijo - 1];
            } else {
                lps[indice] = 0;
                indice++;
            }
        }
        return lps;
    }
}
