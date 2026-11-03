package com.bibliometria.core.matching;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Oráculo de búsqueda ingenua, limitado al código de pruebas. */
final class NaiveMatcher implements StringMatcher {

  @Override
  public MatchResult buscarTodas(String texto, String patron) {
    Objects.requireNonNull(texto, "texto");
    Objects.requireNonNull(patron, "patron");
    long inicio = System.nanoTime();
    List<Integer> posiciones = new ArrayList<>();
    long comparaciones = 0;

    if (patron.isEmpty()) {
      for (int posicion = 0; posicion <= texto.length(); posicion++) {
        posiciones.add(posicion);
      }
    } else {
      for (int inicioVentana = 0;
          inicioVentana <= texto.length() - patron.length();
          inicioVentana++) {
        int indicePatron = 0;
        while (indicePatron < patron.length()) {
          comparaciones++;
          if (texto.charAt(inicioVentana + indicePatron) != patron.charAt(indicePatron)) {
            break;
          }
          indicePatron++;
        }
        if (indicePatron == patron.length()) {
          posiciones.add(inicioVentana);
        }
      }
    }

    long nanosBusqueda = System.nanoTime() - inicio;
    return new MatchResult(posiciones, 0, nanosBusqueda, comparaciones, Map.of());
  }
}
