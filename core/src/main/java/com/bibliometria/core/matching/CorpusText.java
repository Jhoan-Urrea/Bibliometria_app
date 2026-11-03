package com.bibliometria.core.matching;

import java.util.Objects;

/** Texto P-TEXTO del corpus identificado por su clave bibliográfica. */
public record CorpusText(String id, String texto) {

    public CorpusText {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(texto, "texto");
        if (id.isBlank()) {
            throw new IllegalArgumentException("El identificador del texto no puede estar vacío");
        }
    }
}
