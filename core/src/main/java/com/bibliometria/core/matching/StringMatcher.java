package com.bibliometria.core.matching;

/** Contrato para buscar todas las ocurrencias exactas de un patrón en un texto. */
public interface StringMatcher {

  /**
   * Busca todas las ocurrencias, incluidas las solapadas.
   *
   * <p>Las posiciones son índices UTF-16, coherentes con {@link String#charAt(int)}. Un patrón
   * vacío coincide en cada límite del texto, desde cero hasta su longitud.
   *
   * @param texto texto en el que buscar
   * @param patron patrón exacto
   * @return posiciones, tiempos, comparaciones texto-patrón y métricas adicionales
   */
  MatchResult buscarTodas(String texto, String patron);
}
