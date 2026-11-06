package com.bibliometria.core.text;

import java.util.Objects;

/** Implementación de referencia que conserva cada token sin aplicar stemming. */
public final class IdentityStemmer implements Stemmer {

    @Override
    public String normalizeSuffixes(String token) {
        return Objects.requireNonNull(token, "token");
    }
}
