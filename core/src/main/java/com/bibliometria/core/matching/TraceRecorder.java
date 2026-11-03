package com.bibliometria.core.matching;

import java.util.Map;

/** Registra pasos intermedios de los algoritmos de búsqueda. */
@FunctionalInterface
public interface TraceRecorder {

  /** Registra un paso del algoritmo junto con sus datos serializables. */
  void registrar(String algoritmo, String paso, Map<String, Object> datos);

  /** Indica si el registrador conserva los eventos recibidos. */
  default boolean habilitado() {
    return true;
  }

  /** Crea un registrador sin efectos para ejecuciones sin trazabilidad. */
  static TraceRecorder sinTraza() {
    return new TraceRecorder() {
      @Override
      public void registrar(String algoritmo, String paso, Map<String, Object> datos) {}

      @Override
      public boolean habilitado() {
        return false;
      }
    };
  }
}
