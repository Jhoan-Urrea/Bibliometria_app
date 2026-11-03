package com.bibliometria.core.matching;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Búsqueda Knuth-Morris-Pratt con soporte para coincidencias solapadas.
 *
 * <p>Correspondencia con DF §7:
 *
 * <table>
 *   <caption>Fórmulas KMP y código</caption>
 *   <tr><th>Fórmula / variable</th><th>Implementación</th><th>Líneas Java</th></tr>
 *   <tr><td>{@code m = patron.length()}</td><td>Longitud del patrón</td><td>47, 69, 88</td></tr>
 *   <tr><td>{@code n = texto.length()}</td><td>Longitud del texto</td><td>64</td></tr>
 *   <tr><td>{@code LPS[i] = max{k : 0 <= k <= i, P[0..k) = P[i-k+1..i]}}</td>
 *       <td>{@code lps[i]}; prefijo propio más largo que es sufijo</td><td>86-102</td></tr>
 *   <tr><td>{@code T[i] == P[j]}</td><td>Comparación carácter a carácter</td><td>65-66</td></tr>
 *   <tr><td>{@code j = LPS[j-1]}</td><td>Retorno tras desajuste o coincidencia</td><td>71, 74</td></tr>
 * </table>
 *
 * <p>Complejidad teórica (DF §7.4): construir LPS cuesta {@code O(m)}; buscar cuesta {@code O(n)};
 * el total es {@code O(n + m)}. El espacio auxiliar es {@code O(m)} para LPS; el almacenamiento de
 * posiciones de salida no se cuenta. La traza está desactivada por defecto.
 */
public final class KmpMatcher implements StringMatcher {

  private final TraceRecorder traceRecorder;

  public KmpMatcher() {
    this(TraceRecorder.sinTraza());
  }

  public KmpMatcher(TraceRecorder traceRecorder) {
    this.traceRecorder = Objects.requireNonNull(traceRecorder, "traceRecorder");
  }

  @Override
  public MatchResult buscarTodas(String texto, String patron) {
    Objects.requireNonNull(texto, "texto");
    Objects.requireNonNull(patron, "patron");

    long inicioPreproceso = System.nanoTime();
    int[] lps = construirLps(patron);
    if (traceRecorder.habilitado()) {
      traceRecorder.registrar(
          "KMP", "tablaLPS", Map.of("patron", patron, "lps", Arrays.stream(lps).boxed().toList()));
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

  /** Construye en O(m) la tabla de prefijos propios que también son sufijos. */
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
