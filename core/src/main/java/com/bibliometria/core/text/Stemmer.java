package com.bibliometria.core.text;

/** Normaliza sufijos de términos sin cambiar sus demás características. */
@FunctionalInterface
public interface Stemmer {

    String normalizeSuffixes(String token);
}
