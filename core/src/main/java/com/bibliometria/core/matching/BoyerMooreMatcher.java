package com.bibliometria.core.matching;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Búsqueda Boyer-Moore con regla de carácter malo y sufijo bueno fuerte.
 *
 * <p>Correspondencia fórmula-código (AR-F2-11):
 *
 * <table>
 *   <caption>Fórmulas Boyer-Moore y código</caption>
 *   <tr><th>Fórmula / variable</th><th>Implementación Java</th></tr>
 *   <tr><td>{@code m = |P|}, {@code n = |T|}</td>
 *       <td>{@code patron.length()}, {@code texto.length()} en {@link #buscarTodas}</td></tr>
 *   <tr><td>{@code last(c) = max{i : P[i] = c}, -1 si no existe}</td>
 *       <td>{@code ultimaAparicion[c]} en {@link #ultimaAparicion(String)}</td></tr>
 *   <tr><td>{@code suf[i] = longitud de la subcadena que termina en i y coincide con un sufijo de P}</td>
 *       <td>{@code sufijos[i]} en {@code construirSufijos}</td></tr>
 *   <tr><td>{@code shift[j]}; desplazamiento fuerte de sufijo bueno</td>
 *       <td>{@code desplazamientosBuenSufijo[j]} en {@code calcularDesplazamientosBuenSufijo}</td></tr>
 *   <tr><td>{@code max(j - last(T[s+j]), shift[j])}</td>
 *       <td>{@code caracterMalo}, {@code sufijoBueno}, {@code Math.max} en {@link #buscarTodas}</td></tr>
 *   <tr><td>{@code shift[0]}</td><td>Desplazamiento tras coincidencia completa en
 *       {@link #desplazamientoTrasOcurrencia(String)}</td></tr>
 *   <tr><td>{@code P[j] == T[s+j]}</td><td>Comparación de derecha a izquierda en
 *       {@link #buscarTodas}</td></tr>
 * </table>
 *
 * <p>Complejidad: el preprocesamiento cuesta {@code O(m + |Sigma|)}, donde {@code |Sigma| = 65,536}
 * por la tabla densa de caracteres UTF-16. La búsqueda tiene mejor comportamiento promedio
 * sublineal, pero su peor caso sin la optimización Galil es {@code O(n*m)}. El espacio auxiliar es
 * {@code O(m + |Sigma|)}; no incluye las posiciones de salida. La traza se desactiva por defecto.
 */
public final class BoyerMooreMatcher implements StringMatcher {

    private final TraceRecorder traceRecorder;

    /** Creates a matcher with tracing disabled. */
    public BoyerMooreMatcher() {
        this(TraceRecorder.sinTraza());
    }

    /**
     * Creates a matcher with the supplied trace recorder.
     *
     * @param traceRecorder recorder used for algorithm events
     */
    public BoyerMooreMatcher(TraceRecorder traceRecorder) {
        this.traceRecorder = Objects.requireNonNull(traceRecorder, "traceRecorder");
    }

    /**
     * Busca todas las posiciones de {@code P} en {@code T} con las reglas de carácter malo y sufijo
     * bueno fuerte.
     *
     * <p>Tras un desajuste en {@code j}, el desplazamiento es {@code max(1, j - last(T[s+j]),
     * shift[j])}; tras una coincidencia completa usa {@code shift[0]}. Las posiciones son índices
     * UTF-16.
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
        int[] ultimasApariciones = ultimaAparicion(patron);
        int[] desplazamientosBuenSufijo = calcularDesplazamientosBuenSufijo(patron);
        if (traceRecorder.habilitado()) {
            Map<String, Integer> tablaCaracterMalo = new LinkedHashMap<>();
            for (int indice = 0; indice < patron.length(); indice++) {
                char caracter = patron.charAt(indice);
                tablaCaracterMalo.put(String.valueOf(caracter), ultimasApariciones[caracter]);
            }
            traceRecorder.registrar(
                    "Boyer-Moore",
                    "tablas",
                    Map.of(
                            "patron",
                            patron,
                            "ultimaAparicion",
                            tablaCaracterMalo,
                            "buenSufijo",
                            Arrays.stream(
                                            desplazamientosBuenSufijo,
                                            Math.min(1, desplazamientosBuenSufijo.length),
                                            desplazamientosBuenSufijo.length)
                                    .boxed()
                                    .toList(),
                            "desplazamientoTrasOcurrencia",
                            desplazamientosBuenSufijo.length == 0
                                    ? 0
                                    : desplazamientosBuenSufijo[0]));
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
            int desplazamiento = 0;
            while (desplazamiento <= texto.length() - patron.length()) {
                int indicePatron = patron.length() - 1;
                while (indicePatron >= 0) {
                    comparaciones++;
                    if (patron.charAt(indicePatron)
                            != texto.charAt(desplazamiento + indicePatron)) {
                        break;
                    }
                    indicePatron--;
                }

                if (indicePatron < 0) {
                    posiciones.add(desplazamiento);
                    desplazamiento += desplazamientosBuenSufijo[0];
                } else {
                    int caracterMalo =
                            indicePatron
                                    - ultimasApariciones[
                                            texto.charAt(desplazamiento + indicePatron)];
                    int sufijoBueno = desplazamientosBuenSufijo[indicePatron];
                    desplazamiento += Math.max(1, Math.max(caracterMalo, sufijoBueno));
                }
            }
        }
        long nanosBusqueda = System.nanoTime() - inicioBusqueda;

        return new MatchResult(posiciones, nanosPreproceso, nanosBusqueda, comparaciones, Map.of());
    }

    /**
     * Calcula {@code last(c) = max{i : P[i] = c}}, o {@code -1} si {@code c} no aparece.
     *
     * @param patron patrón {@code P} que se preprocesa
     * @return tabla {@code ultimaAparicion[c]} indexada por el valor UTF-16 de {@code c}
     */
    public static int[] ultimaAparicion(String patron) {
        Objects.requireNonNull(patron, "patron");
        int[] ultimaAparicion = new int[Character.MAX_VALUE + 1];
        Arrays.fill(ultimaAparicion, -1);
        for (int indice = 0; indice < patron.length(); indice++) {
            ultimaAparicion[patron.charAt(indice)] = indice;
        }
        return ultimaAparicion;
    }

    /**
     * Devuelve {@code shift[j]} para desajustes en las posiciones {@code 1..m-1}.
     *
     * <p>La posición cero se omite para que el vector conserve la convención del artefacto F2; su
     * desplazamiento también es el que se usa tras una coincidencia completa.
     *
     * @param patron patrón {@code P} que se preprocesa
     * @return desplazamientos de sufijo bueno fuerte para {@code j=1..m-1}
     */
    public static int[] buenSufijo(String patron) {
        Objects.requireNonNull(patron, "patron");
        int[] desplazamientos = calcularDesplazamientosBuenSufijo(patron);
        return desplazamientos.length <= 1
                ? new int[0]
                : Arrays.copyOfRange(desplazamientos, 1, desplazamientos.length);
    }

    /**
     * Devuelve {@code shift[0]}, el desplazamiento seguro tras una coincidencia completa.
     *
     * @param patron patrón {@code P}
     * @return desplazamiento posterior a la ocurrencia
     */
    public static int desplazamientoTrasOcurrencia(String patron) {
        Objects.requireNonNull(patron, "patron");
        int[] desplazamientos = calcularDesplazamientosBuenSufijo(patron);
        return desplazamientos.length == 0 ? 0 : desplazamientos[0];
    }

    private static int[] calcularDesplazamientosBuenSufijo(String patron) {
        int longitud = patron.length();
        if (longitud == 0) {
            return new int[0];
        }

        int[] sufijos = construirSufijos(patron);
        int[] desplazamientos = new int[longitud];
        Arrays.fill(desplazamientos, longitud);

        int siguiente = 0;
        for (int indice = longitud - 1; indice >= -1; indice--) {
            if (indice == -1 || sufijos[indice] == indice + 1) {
                for (; siguiente < longitud - 1 - indice; siguiente++) {
                    desplazamientos[siguiente] = longitud - 1 - indice;
                }
            }
        }
        for (int indice = 0; indice < longitud - 1; indice++) {
            desplazamientos[longitud - 1 - sufijos[indice]] = longitud - 1 - indice;
        }
        return desplazamientos;
    }

    private static int[] construirSufijos(String patron) {
        int longitud = patron.length();
        int[] sufijos = new int[longitud];
        sufijos[longitud - 1] = longitud;
        int limite = longitud - 1;
        int frontera = longitud - 1;

        for (int indice = longitud - 2; indice >= 0; indice--) {
            if (indice > limite && sufijos[indice + longitud - 1 - frontera] < indice - limite) {
                sufijos[indice] = sufijos[indice + longitud - 1 - frontera];
            } else {
                if (indice < limite) {
                    limite = indice;
                }
                frontera = indice;
                while (limite >= 0
                        && patron.charAt(limite)
                                == patron.charAt(limite + longitud - 1 - frontera)) {
                    limite--;
                }
                sufijos[indice] = frontera - limite;
            }
        }
        return sufijos;
    }
}
