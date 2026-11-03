package com.bibliometria.core.matching;

import java.util.List;
import java.util.Objects;

/** Resultado reproducible de búsqueda y comparación de algoritmos sobre un corpus. */
public record CorpusSearchResult(
    String patron, List<CorpusArticleMatches> articulos, boolean coinciden) {

  public CorpusSearchResult {
    Objects.requireNonNull(patron, "patron");
    articulos = List.copyOf(Objects.requireNonNull(articulos, "articulos"));
  }
}
