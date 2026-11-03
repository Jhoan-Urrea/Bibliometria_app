package com.bibliometria.core.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CorpusSearchServiceTest {

  @Test
  void searchesTwentyTextsAndReturnsAgreementAndRelativeHighlight() {
    List<CorpusText> corpus = new ArrayList<>();
    for (int indice = 0; indice < 20; indice++) {
      String texto =
          indice == 0
              ? "Inicio artificial intelligence aparece una vez al final."
              : "Documento " + indice + " sin la frase consultada.";
      corpus.add(new CorpusText("A%02d".formatted(indice + 1), texto));
    }

    CorpusSearchResult resultado =
        new CorpusSearchService().buscar(corpus, "artificial intelligence");

    assertThat(resultado.coinciden()).isTrue();
    assertThat(resultado.articulos()).hasSize(20);
    assertThat(resultado.articulos().getFirst().posicionesPorAlgoritmo())
        .containsEntry("KMP", List.of(7))
        .containsEntry("Boyer-Moore", List.of(7))
        .containsEntry("Rabin-Karp", List.of(7));
    assertThat(resultado.articulos().getFirst().fragmentos())
        .singleElement()
        .satisfies(
            fragmento -> {
              assertThat(fragmento.texto()).hasSizeLessThanOrEqualTo(40);
              assertThat(
                      copiar(
                          fragmento.texto(), fragmento.inicioResaltado(), fragmento.finResaltado()))
                  .isEqualTo("artificial intelligence");
            });
  }

  @Test
  void rejectsNullCorpusEntriesAndPatterns() {
    CorpusSearchService service = new CorpusSearchService();

    assertThatThrownBy(() -> service.buscar(null, "a")).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> service.buscar(List.of(new CorpusText("A01", "texto")), null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> service.buscar(java.util.Arrays.asList((CorpusText) null), "a"))
        .isInstanceOf(NullPointerException.class);
  }

  private static String copiar(String texto, int inicio, int fin) {
    StringBuilder resultado = new StringBuilder(fin - inicio);
    for (int indice = inicio; indice < fin; indice++) {
      resultado.append(texto.charAt(indice));
    }
    return resultado.toString();
  }
}
