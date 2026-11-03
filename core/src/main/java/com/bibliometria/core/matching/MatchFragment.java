package com.bibliometria.core.matching;

import java.util.Objects;

/** Fragmento de texto con el intervalo resaltado expresado en índices relativos. */
public record MatchFragment(int inicioTexto, String texto, int inicioResaltado, int finResaltado) {

  public MatchFragment {
    Objects.requireNonNull(texto, "texto");
    if (inicioTexto < 0
        || inicioResaltado < 0
        || finResaltado < inicioResaltado
        || finResaltado > texto.length()) {
      throw new IllegalArgumentException("Los límites del fragmento no son válidos");
    }
  }
}
