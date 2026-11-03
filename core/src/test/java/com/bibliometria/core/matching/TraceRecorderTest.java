package com.bibliometria.core.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TraceRecorderTest {

  @Test
  void exportsLpsBoyerMooreAndRollingHashStepsAsJson() {
    JsonTraceRecorder recorder = new JsonTraceRecorder();

    new KmpMatcher(recorder).buscarTodas("ababaca", "ababaca");
    new BoyerMooreMatcher(recorder).buscarTodas("ababaca", "ababaca");
    new RabinKarpMatcher(recorder).buscarTodas("ababaca", "ababaca");

    List<java.util.Map<String, Object>> eventos = recorder.eventos();
    assertThat(eventos)
        .extracting(evento -> evento.get("algoritmo"))
        .contains("KMP", "Boyer-Moore", "Rabin-Karp");
    assertThat(recorder.exportarJson())
        .startsWith("[")
        .contains("\"paso\":\"tablaLPS\"")
        .contains("\"paso\":\"tablas\"")
        .contains("\"paso\":\"hashPatron\"")
        .contains("\"paso\":\"ventana\"")
        .endsWith("]");
    Object datosEventoBm =
        eventos.stream()
            .filter(evento -> evento.get("algoritmo").equals("Boyer-Moore"))
            .findFirst()
            .orElseThrow()
            .get("datos");
    assertThat(datosEventoBm).isInstanceOf(java.util.Map.class);
    java.util.Map<?, ?> datosBm = (java.util.Map<?, ?>) datosEventoBm;
    assertThat(datosBm.get("buenSufijo")).isEqualTo(List.of(6, 6, 6, 6, 2, 1));
  }

  @Test
  void escapesControlCharactersAndQuotesInJsonStrings() {
    JsonTraceRecorder recorder = new JsonTraceRecorder();
    recorder.registrar("KMP", "paso\n1", java.util.Map.of("patron", "a\"b\\c"));

    assertThat(recorder.exportarJson())
        .isEqualTo(
            "[{\"algoritmo\":\"KMP\",\"paso\":\"paso\\n1\",\"datos\":{\"patron\":\"a\\\"b\\\\c\"}}]");
  }
}
