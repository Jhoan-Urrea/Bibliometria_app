package com.bibliometria.core.matching;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Posiciones por algoritmo y fragmentos resaltables de un artículo del corpus. */
public record CorpusArticleMatches(
        String id,
        Map<String, List<Integer>> posicionesPorAlgoritmo,
        List<MatchFragment> fragmentos) {

    public CorpusArticleMatches {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(posicionesPorAlgoritmo, "posicionesPorAlgoritmo");
        Objects.requireNonNull(fragmentos, "fragmentos");
        Map<String, List<Integer>> copia = new LinkedHashMap<>();
        posicionesPorAlgoritmo.forEach(
                (algoritmo, posiciones) ->
                        copia.put(
                                Objects.requireNonNull(algoritmo, "algoritmo"),
                                List.copyOf(Objects.requireNonNull(posiciones, "posiciones"))));
        posicionesPorAlgoritmo = Collections.unmodifiableMap(copia);
        fragmentos = List.copyOf(fragmentos);
    }
}
