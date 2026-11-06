package com.bibliometria.core.text;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Resultado inmutable de los perfiles P-TEXTO y P-TOKENS. */
public record ProcessedDocument(
        String id,
        String textoNormalizado,
        List<String> tokens,
        Set<String> conjunto,
        Map<String, Integer> frecuencias,
        String cadenaCanonica) {

    public ProcessedDocument {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(textoNormalizado, "textoNormalizado");
        Objects.requireNonNull(cadenaCanonica, "cadenaCanonica");
        tokens = List.copyOf(Objects.requireNonNull(tokens, "tokens"));
        conjunto =
                Collections.unmodifiableSet(
                        new LinkedHashSet<>(Objects.requireNonNull(conjunto, "conjunto")));
        frecuencias =
                Collections.unmodifiableMap(
                        new LinkedHashMap<>(Objects.requireNonNull(frecuencias, "frecuencias")));
        if (tokens.stream().anyMatch(Objects::isNull)
                || conjunto.stream().anyMatch(Objects::isNull)
                || frecuencias.entrySet().stream()
                        .anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)) {
            throw new IllegalArgumentException("El resultado no puede contener valores null");
        }
    }
}
