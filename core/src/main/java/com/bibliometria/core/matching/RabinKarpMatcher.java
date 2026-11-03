package com.bibliometria.core.matching;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Búsqueda Rabin-Karp con hash rodante y verificación exacta de cada hash coincidente.
 *
 * <p>Correspondencia con DF §7:
 *
 * <table>
 *   <caption>Fórmulas Rabin-Karp y código</caption>
 *   <tr><th>Fórmula / variable</th><th>Implementación</th><th>Líneas Java</th></tr>
 *   <tr><td>{@code m = |P|}, {@code n = |T|}</td>
 *       <td>{@code patron.length()}, {@code texto.length()}</td><td>58, 93</td></tr>
 *   <tr><td>{@code B = 257}, {@code q = 1,000,000,007}</td>
 *       <td>{@code BASE}, {@code MODULO}</td><td>36-37</td></tr>
 *   <tr><td>{@code H(P) = sum(P[i] * B^(m-1-i)) mod q}</td>
 *       <td>{@code hashPatron}; potencia de salida {@code potenciaMayor}</td><td>55-63</td></tr>
 *   <tr><td>{@code H(T[s+1..s+m]) = ((H(T[s..s+m-1]) - T[s]*B^(m-1))*B + T[s+m]) mod q}</td>
 *       <td>{@code hashSinInicial} y actualización de {@code hashVentana}</td><td>123-125</td></tr>
 *   <tr><td>{@code H(ventana) == H(P)}</td><td>{@code hashCoincide}</td><td>94</td></tr>
 *   <tr><td>{@code T[s+i] == P[i]}</td><td>Verificación exacta y contador {@code comparaciones}</td>
 *       <td>108-110</td></tr>
 * </table>
 *
 * <p>Complejidad teórica (DF §7.4): preprocesamiento {@code O(m)} y recorrido rodante {@code
 * O(n-m+1)}; bajo distribución uniforme de hashes se espera {@code O(n + m)} total. En el peor
 * caso, las colisiones verificadas carácter a carácter cuestan {@code O(n*m)}. El espacio auxiliar
 * es {@code O(1)} sin contar posiciones ni eventos de traza; la traza se desactiva por defecto.
 */
public final class RabinKarpMatcher implements StringMatcher {

    private static final long BASE = 257;
    private static final long MODULO = 1_000_000_007;

    private final TraceRecorder traceRecorder;

    public RabinKarpMatcher() {
        this(TraceRecorder.sinTraza());
    }

    public RabinKarpMatcher(TraceRecorder traceRecorder) {
        this.traceRecorder = Objects.requireNonNull(traceRecorder, "traceRecorder");
    }

    @Override
    public MatchResult buscarTodas(String texto, String patron) {
        Objects.requireNonNull(texto, "texto");
        Objects.requireNonNull(patron, "patron");

        long inicioPreproceso = System.nanoTime();
        long hashPatron = 0;
        long potenciaMayor = 1;
        for (int indice = 0; indice < patron.length(); indice++) {
            hashPatron = (hashPatron * BASE + patron.charAt(indice)) % MODULO;
            if (indice < patron.length() - 1) {
                potenciaMayor = potenciaMayor * BASE % MODULO;
            }
        }
        if (traceRecorder.habilitado()) {
            traceRecorder.registrar(
                    "Rabin-Karp",
                    "hashPatron",
                    Map.of(
                            "patron", patron,
                            "base", BASE,
                            "modulo", MODULO,
                            "hash", hashPatron,
                            "potenciaMayor", potenciaMayor));
        }
        long nanosPreproceso = System.nanoTime() - inicioPreproceso;

        long inicioBusqueda = System.nanoTime();
        List<Integer> posiciones = new ArrayList<>();
        long comparaciones = 0;
        long hashCoincidencias = 0;
        long falsosPositivos = 0;

        if (patron.isEmpty()) {
            for (int posicion = 0; posicion <= texto.length(); posicion++) {
                posiciones.add(posicion);
            }
        } else if (patron.length() <= texto.length()) {
            long hashVentana = 0;
            for (int indice = 0; indice < patron.length(); indice++) {
                hashVentana = (hashVentana * BASE + texto.charAt(indice)) % MODULO;
            }

            int ultimaVentana = texto.length() - patron.length();
            for (int inicio = 0; inicio <= ultimaVentana; inicio++) {
                boolean hashCoincide = hashVentana == hashPatron;
                if (traceRecorder.habilitado()) {
                    traceRecorder.registrar(
                            "Rabin-Karp",
                            "ventana",
                            Map.of(
                                    "inicio", inicio,
                                    "hash", hashVentana,
                                    "coincideHash", hashCoincide));
                }
                if (hashCoincide) {
                    hashCoincidencias++;
                    boolean coincide = true;
                    for (int indice = 0; indice < patron.length(); indice++) {
                        comparaciones++;
                        if (texto.charAt(inicio + indice) != patron.charAt(indice)) {
                            coincide = false;
                            break;
                        }
                    }
                    if (coincide) {
                        posiciones.add(inicio);
                    } else {
                        falsosPositivos++;
                    }
                }

                if (inicio < ultimaVentana) {
                    long caracterInicial = texto.charAt(inicio);
                    long hashSinInicial =
                            (hashVentana - caracterInicial * potenciaMayor % MODULO + MODULO)
                                    % MODULO;
                    hashVentana =
                            (hashSinInicial * BASE + texto.charAt(inicio + patron.length()))
                                    % MODULO;
                }
            }
        }

        long nanosBusqueda = System.nanoTime() - inicioBusqueda;
        Map<String, Object> extras =
                Map.of("hashCoincidencias", hashCoincidencias, "falsosPositivos", falsosPositivos);
        return new MatchResult(posiciones, nanosPreproceso, nanosBusqueda, comparaciones, extras);
    }
}
