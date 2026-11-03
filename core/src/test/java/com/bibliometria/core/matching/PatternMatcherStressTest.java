package com.bibliometria.core.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PatternMatcherStressTest {

  private static final int CANTIDAD_CASOS = 10_000;

  private static final List<StringMatcher> MATCHERS =
      List.of(new KmpMatcher(), new BoyerMooreMatcher(), new RabinKarpMatcher());

  @Test
  void comparesAllAlgorithmsAgainstTheOracleForTenThousandFixedSeedCases() {
    Random random = new Random(42);
    char[] alfabeto = {'a', 'b', 'c', 'd', 'ñ', 'á', '\uD83D', '\uDE00'};
    NaiveMatcher oraculo = new NaiveMatcher();
    long inicio = System.nanoTime();

    for (int caso = 0; caso < CANTIDAD_CASOS; caso++) {
      String texto = generar(random, alfabeto, random.nextInt(81));
      String patron = generar(random, alfabeto, random.nextInt(25));
      List<Integer> posicionesEsperadas = oraculo.buscarTodas(texto, patron).posiciones();

      for (StringMatcher matcher : MATCHERS) {
        assertThat(matcher.buscarTodas(texto, patron).posiciones())
            .as("caso %s, algoritmo %s", caso, matcher.getClass().getSimpleName())
            .isEqualTo(posicionesEsperadas);
      }
    }

    long milisegundos = (System.nanoTime() - inicio) / 1_000_000;
    assertThat(milisegundos).as("duración de los 10.000 casos").isLessThan(60_000);
  }

  private static String generar(Random random, char[] alfabeto, int longitud) {
    StringBuilder resultado = new StringBuilder(longitud);
    for (int indice = 0; indice < longitud; indice++) {
      resultado.append(alfabeto[random.nextInt(alfabeto.length)]);
    }
    return resultado.toString();
  }
}
