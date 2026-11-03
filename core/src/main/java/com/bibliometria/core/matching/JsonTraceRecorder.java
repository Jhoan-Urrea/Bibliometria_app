package com.bibliometria.core.matching;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Acumula eventos de algoritmos y los exporta como JSON UTF-8 compatible. */
public final class JsonTraceRecorder implements TraceRecorder {

  private final List<Map<String, Object>> eventos = new ArrayList<>();

  @Override
  public synchronized void registrar(String algoritmo, String paso, Map<String, Object> datos) {
    Objects.requireNonNull(algoritmo, "algoritmo");
    Objects.requireNonNull(paso, "paso");
    Objects.requireNonNull(datos, "datos");
    Map<String, Object> evento = new LinkedHashMap<>();
    evento.put("algoritmo", algoritmo);
    evento.put("paso", paso);
    evento.put("datos", Collections.unmodifiableMap(new LinkedHashMap<>(datos)));
    eventos.add(Collections.unmodifiableMap(evento));
  }

  /** Devuelve una instantánea inmutable de los eventos registrados. */
  public synchronized List<Map<String, Object>> eventos() {
    return List.copyOf(eventos);
  }

  /** Exporta todos los eventos registrados como un arreglo JSON. */
  public synchronized String exportarJson() {
    StringBuilder json = new StringBuilder();
    json.append('[');
    for (int indice = 0; indice < eventos.size(); indice++) {
      if (indice > 0) {
        json.append(',');
      }
      escribirValor(json, eventos.get(indice));
    }
    return json.append(']').toString();
  }

  private static void escribirValor(StringBuilder json, Object valor) {
    if (valor == null) {
      json.append("null");
    } else if (valor instanceof String texto) {
      escribirCadena(json, texto);
    } else if (valor instanceof Number || valor instanceof Boolean) {
      json.append(valor);
    } else if (valor instanceof Map<?, ?> mapa) {
      json.append('{');
      boolean primero = true;
      for (Map.Entry<?, ?> entrada : mapa.entrySet()) {
        if (!(entrada.getKey() instanceof String clave)) {
          throw new IllegalArgumentException("Las claves JSON deben ser cadenas");
        }
        if (!primero) {
          json.append(',');
        }
        escribirCadena(json, clave);
        json.append(':');
        escribirValor(json, entrada.getValue());
        primero = false;
      }
      json.append('}');
    } else if (valor instanceof Iterable<?> elementos) {
      json.append('[');
      boolean primero = true;
      for (Object elemento : elementos) {
        if (!primero) {
          json.append(',');
        }
        escribirValor(json, elemento);
        primero = false;
      }
      json.append(']');
    } else {
      throw new IllegalArgumentException(
          "Tipo no serializable en la traza: " + valor.getClass().getName());
    }
  }

  private static void escribirCadena(StringBuilder json, String texto) {
    json.append('"');
    for (int indice = 0; indice < texto.length(); indice++) {
      char caracter = texto.charAt(indice);
      switch (caracter) {
        case '"' -> json.append("\\\"");
        case '\\' -> json.append("\\\\");
        case '\b' -> json.append("\\b");
        case '\f' -> json.append("\\f");
        case '\n' -> json.append("\\n");
        case '\r' -> json.append("\\r");
        case '\t' -> json.append("\\t");
        default -> {
          if (caracter < 0x20) {
            json.append("\\u");
            String hex = Integer.toHexString(caracter);
            for (int relleno = hex.length(); relleno < 4; relleno++) {
              json.append('0');
            }
            json.append(hex);
          } else {
            json.append(caracter);
          }
        }
      }
    }
    json.append('"');
  }
}
