package com.bibliometria.core.matching;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Resultado inmutable de una búsqueda exacta de patrones. */
public record MatchResult(
        List<Integer> posiciones,
        long nanosPreproceso,
        long nanosBusqueda,
        long comparaciones,
        Map<String, Object> extras) {

    public MatchResult {
        posiciones = List.copyOf(Objects.requireNonNull(posiciones, "posiciones"));
        extras =
                Collections.unmodifiableMap(
                        new LinkedHashMap<>(Objects.requireNonNull(extras, "extras")));
        if (posiciones.stream().anyMatch(Objects::isNull)
                || posiciones.stream().anyMatch(posicion -> posicion < 0)) {
            throw new IllegalArgumentException("Las posiciones no pueden ser null ni negativas");
        }
        if (nanosPreproceso < 0 || nanosBusqueda < 0 || comparaciones < 0) {
            throw new IllegalArgumentException(
                    "Los tiempos y comparaciones no pueden ser negativos");
        }
        if (extras.keySet().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Las claves de extras no pueden ser null");
        }
    }
}
