package com.bibliometria.core.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ForbiddenApiScanTest {

  private static final List<String> FORBIDDEN_METHODS =
      List.of(
          "indexOf",
          "lastIndexOf",
          "contains",
          "matches",
          "split",
          "replace",
          "startsWith",
          "endsWith");

  @TempDir Path temporaryDirectory;

  @Test
  void matchingProductionSourcesDoNotCallForbiddenStringApis() throws IOException {
    Path matchingSources = Path.of("src/main/java/com/bibliometria/core/matching");

    assertThat(Files.isDirectory(matchingSources)).isTrue();
    validarSinApisProhibidas(matchingSources);
  }

  @Test
  void detectsForbiddenApiCallsInTemporarySource() throws IOException {
    Path source = temporaryDirectory.resolve("Offender.java");
    Files.writeString(
        source,
        "class Offender { int buscar(String texto) { return texto.indexOf(\"x\"); } }",
        StandardCharsets.UTF_8);

    assertThatThrownBy(() -> validarSinApisProhibidas(temporaryDirectory))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining(source + ":1: llamada prohibida a indexOf");
  }

  @Test
  void detectsRegexPackageReferencesInTemporarySource() throws IOException {
    Path source = temporaryDirectory.resolve("RegexOffender.java");
    Files.writeString(
        source, "import java.util.regex.Pattern;\nclass RegexOffender {}", StandardCharsets.UTF_8);

    assertThatThrownBy(() -> validarSinApisProhibidas(temporaryDirectory))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining(source + ":1: paquete prohibido java.util.regex");
  }

  private static void validarSinApisProhibidas(Path directorio) throws IOException {
    List<String> violaciones = escanear(directorio);
    if (!violaciones.isEmpty()) {
      throw new AssertionError("APIs de String prohibidas: " + violaciones);
    }
  }

  private static List<String> escanear(Path directorio) throws IOException {
    List<String> violaciones = new ArrayList<>();
    try (var archivos = Files.walk(directorio)) {
      for (Path archivo :
          archivos
              .filter(Files::isRegularFile)
              .filter(path -> path.toString().endsWith(".java"))
              .toList()) {
        String fuente = Files.readString(archivo, StandardCharsets.UTF_8);
        int regexOffset = buscarSecuencia(fuente, "java.util.regex");
        if (regexOffset >= 0) {
          violaciones.add(
              archivo
                  + ":"
                  + contarLinea(fuente, regexOffset)
                  + ": paquete prohibido java.util.regex");
        }
        for (int indice = 0; indice < fuente.length(); indice++) {
          if (fuente.charAt(indice) != '.') {
            continue;
          }
          int inicioNombre = saltarEspacios(fuente, indice + 1);
          for (String metodo : FORBIDDEN_METHODS) {
            if (coincideNombre(fuente, inicioNombre, metodo)) {
              int despuesNombre = saltarEspacios(fuente, inicioNombre + metodo.length());
              if (despuesNombre < fuente.length() && fuente.charAt(despuesNombre) == '(') {
                violaciones.add(
                    archivo
                        + ":"
                        + contarLinea(fuente, indice)
                        + ": llamada prohibida a "
                        + metodo);
              }
            }
          }
        }
      }
    }
    return List.copyOf(violaciones);
  }

  private static int buscarSecuencia(String fuente, String secuencia) {
    for (int inicio = 0; inicio <= fuente.length() - secuencia.length(); inicio++) {
      int indice = 0;
      while (indice < secuencia.length()
          && fuente.charAt(inicio + indice) == secuencia.charAt(indice)) {
        indice++;
      }
      if (indice == secuencia.length()) {
        return inicio;
      }
    }
    return -1;
  }

  private static int contarLinea(String texto, int finExclusivo) {
    int linea = 1;
    for (int indice = 0; indice < finExclusivo; indice++) {
      if (texto.charAt(indice) == '\n') {
        linea++;
      }
    }
    return linea;
  }

  private static int saltarEspacios(String texto, int indice) {
    while (indice < texto.length() && Character.isWhitespace(texto.charAt(indice))) {
      indice++;
    }
    return indice;
  }

  private static boolean coincideNombre(String fuente, int inicio, String metodo) {
    if (inicio + metodo.length() > fuente.length()) {
      return false;
    }
    for (int indice = 0; indice < metodo.length(); indice++) {
      if (fuente.charAt(inicio + indice) != metodo.charAt(indice)) {
        return false;
      }
    }
    int fin = inicio + metodo.length();
    return fin == fuente.length() || !Character.isJavaIdentifierPart(fuente.charAt(fin));
  }
}
