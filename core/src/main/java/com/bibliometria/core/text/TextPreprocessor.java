package com.bibliometria.core.text;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Aplica perfiles de texto deterministas e independientes de infraestructura.
 *
 * <p>La normalización es idempotente: una vez convertidos Unicode, guiones de fin de línea y
 * espacios a su forma estable, repetir P-TEXTO no altera el resultado. P-TOKENS usa reglas
 * explícitas de caracteres, minúsculas con {@link Locale#ROOT} y un conjunto inmutable de
 * stopwords; por ello, los mismos datos y archivo de stopwords producen siempre la misma secuencia
 * y cadena canónica.
 */
public final class TextPreprocessor {

    private final Set<String> stopwords;
    private final Stemmer stemmer;

    /** Crea un preprocesador usando las stopwords versionadas en el classpath. */
    public TextPreprocessor() {
        try {
            this.stopwords = StopwordLoader.loadDefault();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "No se pudieron cargar las stopwords del classpath", exception);
        }
        this.stemmer = new IdentityStemmer();
    }

    static TextPreprocessor withoutStopwords() {
        return new TextPreprocessor(Set.of(), new IdentityStemmer());
    }

    /**
     * Carga stopwords UTF-8 desde un flujo de entrada. El llamador conserva la propiedad del flujo.
     *
     * @param stopwordsInput flujo con una palabra por línea
     * @throws IOException si no se puede leer o procesar el archivo
     */
    public TextPreprocessor(InputStream stopwordsInput) throws IOException {
        this(StopwordLoader.load(stopwordsInput), new IdentityStemmer());
    }

    TextPreprocessor(Set<String> stopwords, Stemmer stemmer) {
        this.stopwords = Set.copyOf(Objects.requireNonNull(stopwords, "stopwords"));
        this.stemmer = Objects.requireNonNull(stemmer, "stemmer");
    }

    /**
     * Normaliza el contenido bibliográfico del artículo y calcula sus tokens.
     *
     * <p>El texto de contenido es la concatenación de título y resumen; los autores permanecen como
     * metadato y no contaminan el vocabulario del documento.
     *
     * @param article artículo de entrada
     * @return documento con campos normalizados, tokens, conjunto, frecuencias y representación
     *     canónica
     */
    public ProcessedDocument process(Article article) {
        Objects.requireNonNull(article, "article");
        String textoNormalizado = normalizeText(article.title() + " " + article.abstractText());
        List<String> tokens =
                tokenize(textoNormalizado.toLowerCase(Locale.ROOT)).stream()
                        .map(stemmer::normalizeSuffixes)
                        .filter(token -> !stopwords.contains(token))
                        .toList();

        Set<String> conjunto = new LinkedHashSet<>(tokens);
        Map<String, Integer> frecuencias = new LinkedHashMap<>();
        for (String token : tokens) {
            frecuencias.merge(token, 1, Integer::sum);
        }
        return new ProcessedDocument(
                article.id(),
                textoNormalizado,
                tokens,
                conjunto,
                frecuencias,
                String.join(" ", tokens));
    }

    /**
     * Aplica N1-N3 para producir P-TEXTO.
     *
     * @param text texto de entrada
     * @return texto NFKC, con palabras cortadas unidas y espacios colapsados
     */
    public String normalizeText(String text) {
        Objects.requireNonNull(text, "text");
        String nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC);
        return collapseWhitespace(joinLineHyphenatedWords(nfkc));
    }

    /**
     * Tokeniza P-TEXTO en minúsculas sin aplicar stopwords.
     *
     * @param textoNormalizado texto P-TEXTO
     * @return tokens en orden, incluyendo repeticiones
     */
    public List<String> tokenizeNormalizedText(String textoNormalizado) {
        Objects.requireNonNull(textoNormalizado, "textoNormalizado");
        return tokenize(textoNormalizado.toLowerCase(Locale.ROOT)).stream()
                .map(stemmer::normalizeSuffixes)
                .toList();
    }

    private String joinLineHyphenatedWords(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            int width = Character.charCount(codePoint);
            if (codePoint == '-'
                    && result.length() > 0
                    && Character.isLetter(Character.codePointBefore(result, result.length()))) {
                int newlineOffset = offset + width;
                int nextOffset = newlineOffset;
                if (nextOffset < text.length() && text.charAt(nextOffset) == '\r') {
                    nextOffset++;
                }
                if (nextOffset < text.length() && text.charAt(nextOffset) == '\n') {
                    nextOffset++;
                    if (nextOffset < text.length()
                            && Character.isLetter(text.codePointAt(nextOffset))) {
                        offset = nextOffset;
                        continue;
                    }
                }
            }
            result.appendCodePoint(codePoint);
            offset += width;
        }
        return result.toString();
    }

    private String collapseWhitespace(String text) {
        StringBuilder result = new StringBuilder(text.length());
        boolean pendingSpace = false;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                if (!result.isEmpty()) {
                    pendingSpace = true;
                }
            } else {
                if (pendingSpace) {
                    result.append(' ');
                    pendingSpace = false;
                }
                result.appendCodePoint(codePoint);
            }
        }
        return result.toString();
    }

    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            int type = Character.getType(codePoint);
            boolean mark =
                    type == Character.NON_SPACING_MARK
                            || type == Character.COMBINING_SPACING_MARK
                            || type == Character.ENCLOSING_MARK;
            if (Character.isLetterOrDigit(codePoint) || (mark && !token.isEmpty())) {
                token.appendCodePoint(codePoint);
            } else if (codePoint == '-'
                    && !token.isEmpty()
                    && hasTrailingLetterOrDigit(token)
                    && offset < text.length()
                    && Character.isLetterOrDigit(text.codePointAt(offset))) {
                token.append('-');
            } else if (!token.isEmpty()) {
                tokens.add(token.toString());
                token.setLength(0);
            }
        }
        if (!token.isEmpty()) {
            tokens.add(token.toString());
        }
        return List.copyOf(tokens);
    }

    private boolean hasTrailingLetterOrDigit(StringBuilder token) {
        int offset = token.length();
        while (offset > 0) {
            int codePoint = token.codePointBefore(offset);
            if (Character.isLetterOrDigit(codePoint)) {
                return true;
            }
            int type = Character.getType(codePoint);
            if (type != Character.NON_SPACING_MARK
                    && type != Character.COMBINING_SPACING_MARK
                    && type != Character.ENCLOSING_MARK) {
                return false;
            }
            offset -= Character.charCount(codePoint);
        }
        return false;
    }
}
