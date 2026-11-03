package com.bibliometria.core.matching;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Ejecuta los tres algoritmos de búsqueda exacta sobre textos P-TEXTO del corpus. */
public final class CorpusSearchService {

  private static final int LONGITUD_FRAGMENTO = 40;
  private final KmpMatcher kmpMatcher;
  private final BoyerMooreMatcher boyerMooreMatcher;
  private final RabinKarpMatcher rabinKarpMatcher;

  public CorpusSearchService() {
    this(TraceRecorder.sinTraza());
  }

  public CorpusSearchService(TraceRecorder traceRecorder) {
    Objects.requireNonNull(traceRecorder, "traceRecorder");
    kmpMatcher = new KmpMatcher(traceRecorder);
    boyerMooreMatcher = new BoyerMooreMatcher(traceRecorder);
    rabinKarpMatcher = new RabinKarpMatcher(traceRecorder);
  }

  /**
   * Busca el patrón en los textos recibidos y marca si los algoritmos obtuvieron idénticas
   * posiciones para cada artículo. Cada fragmento tiene como máximo 40 unidades UTF-16 y resalta la
   * intersección visible con la coincidencia.
   */
  public CorpusSearchResult buscar(List<CorpusText> corpus, String patron) {
    Objects.requireNonNull(corpus, "corpus");
    Objects.requireNonNull(patron, "patron");
    List<CorpusArticleMatches> resultados = new ArrayList<>();
    boolean coinciden = true;

    for (CorpusText articulo : corpus) {
      Objects.requireNonNull(articulo, "articulo");
      List<Integer> posicionesKmp = kmpMatcher.buscarTodas(articulo.texto(), patron).posiciones();
      List<Integer> posicionesBm =
          boyerMooreMatcher.buscarTodas(articulo.texto(), patron).posiciones();
      List<Integer> posicionesRk =
          rabinKarpMatcher.buscarTodas(articulo.texto(), patron).posiciones();

      Map<String, List<Integer>> posiciones = new LinkedHashMap<>();
      posiciones.put("KMP", posicionesKmp);
      posiciones.put("Boyer-Moore", posicionesBm);
      posiciones.put("Rabin-Karp", posicionesRk);
      coinciden &= posicionesKmp.equals(posicionesBm) && posicionesKmp.equals(posicionesRk);
      resultados.add(
          new CorpusArticleMatches(
              articulo.id(), posiciones, crearFragmentos(articulo.texto(), patron, posicionesKmp)));
    }
    return new CorpusSearchResult(patron, resultados, coinciden);
  }

  private static List<MatchFragment> crearFragmentos(
      String texto, String patron, List<Integer> posiciones) {
    List<MatchFragment> fragmentos = new ArrayList<>();
    for (int posicion : posiciones) {
      int inicio = Math.max(0, posicion - (LONGITUD_FRAGMENTO - patron.length()) / 2);
      if (patron.length() > LONGITUD_FRAGMENTO) {
        inicio = posicion;
      }
      int fin = Math.min(texto.length(), inicio + LONGITUD_FRAGMENTO);
      if (fin - inicio < LONGITUD_FRAGMENTO) {
        inicio = Math.max(0, fin - LONGITUD_FRAGMENTO);
      }
      StringBuilder contenido = new StringBuilder(fin - inicio);
      for (int indice = inicio; indice < fin; indice++) {
        contenido.append(texto.charAt(indice));
      }
      int resaltadoInicio = Math.max(posicion, inicio) - inicio;
      int resaltadoFin = Math.min(posicion + patron.length(), fin) - inicio;
      fragmentos.add(
          new MatchFragment(inicio, contenido.toString(), resaltadoInicio, resaltadoFin));
    }
    return List.copyOf(fragmentos);
  }
}
