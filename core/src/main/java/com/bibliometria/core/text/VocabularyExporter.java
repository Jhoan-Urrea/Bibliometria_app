package com.bibliometria.core.text;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Exporta vocabulario y textos P-TEXTO desde el corpus JSON usando solo el JDK. */
public final class VocabularyExporter {

    private static final String VOCABULARY_FILE = "vocabulario.txt";
    private static final String TEXTS_FILE = "textos_p_texto.json";

    private final TextPreprocessor preprocessor;

    public VocabularyExporter() {
        this(TextPreprocessor.withoutStopwords());
    }

    public VocabularyExporter(TextPreprocessor preprocessor) {
        this.preprocessor = Objects.requireNonNull(preprocessor, "preprocessor");
    }

    /**
     * Exporta todos los textos P-TEXTO y el conjunto determinista de tokens únicos.
     *
     * @param corpusJson ruta a un arreglo JSON con campos {@code id}, {@code title} y {@code
     *     abstractText}
     * @param outputDirectory directorio de salida
     * @return SHA-256 del archivo fuente
     * @throws IOException si el corpus no es válido o no se pueden escribir los artefactos
     */
    public String exportar(Path corpusJson, Path outputDirectory) throws IOException {
        Objects.requireNonNull(corpusJson, "corpusJson");
        Objects.requireNonNull(outputDirectory, "outputDirectory");

        String source = Files.readString(corpusJson, StandardCharsets.UTF_8);
        String corpusSha256 = sha256(source.getBytes(StandardCharsets.UTF_8));
        List<CorpusArticle> articles = new CorpusJsonParser(source).parse();
        TreeSet<String> vocabulary = new TreeSet<>();
        List<ExportedText> texts = new ArrayList<>(articles.size());

        for (CorpusArticle article : articles) {
            String pText =
                    preprocessor.normalizeText(article.title() + " " + article.abstractText());
            vocabulary.addAll(preprocessor.tokenizeNormalizedText(pText));
            texts.add(new ExportedText(article.id(), pText));
        }

        String vocabularyContent = String.join("\n", vocabulary);
        if (!vocabulary.isEmpty()) {
            vocabularyContent += "\n";
        }
        String textsContent = serializeTexts(corpusSha256, texts);
        Files.createDirectories(outputDirectory);
        writeAtomically(outputDirectory.resolve(VOCABULARY_FILE), vocabularyContent);
        writeAtomically(outputDirectory.resolve(TEXTS_FILE), textsContent);
        return corpusSha256;
    }

    /** Ejecuta la exportación desde la raíz del proyecto o rutas explícitas. */
    public static void main(String[] args) throws IOException {
        if (args.length != 0 && args.length != 2) {
            throw new IllegalArgumentException(
                    "Uso: VocabularyExporter [<ruta-corpus.json> <directorio-salida>]");
        }
        Path corpus = args.length == 0 ? Path.of("data/corpus.json") : Path.of(args[0]);
        Path output = args.length == 0 ? Path.of("data/embeddings") : Path.of(args[1]);
        String sha256 = new VocabularyExporter().exportar(corpus, output);
        System.out.println("Exportación completada; SHA-256 del corpus: " + sha256);
    }

    private static String serializeTexts(String corpusSha256, List<ExportedText> texts) {
        StringBuilder json = new StringBuilder();
        json.append("{\"corpusSha256\":");
        appendJsonString(json, corpusSha256);
        json.append(",\"documents\":[");
        for (int indice = 0; indice < texts.size(); indice++) {
            if (indice > 0) {
                json.append(',');
            }
            ExportedText text = texts.get(indice);
            json.append("{\"id\":");
            appendJsonString(json, text.id());
            json.append(",\"textoPTexto\":");
            appendJsonString(json, text.text());
            json.append('}');
        }
        return json.append("]}\n").toString();
    }

    private static void appendJsonString(StringBuilder json, String value) {
        json.append('"');
        for (int indice = 0; indice < value.length(); indice++) {
            char current = value.charAt(indice);
            switch (current) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (current < 0x20) {
                        json.append("\\u");
                        String hex = Integer.toHexString(current);
                        for (int relleno = hex.length(); relleno < 4; relleno++) {
                            json.append('0');
                        }
                        json.append(hex);
                    } else {
                        json.append(current);
                    }
                }
            }
        }
        json.append('"');
    }

    private static void writeAtomically(Path destination, String content) throws IOException {
        Path temporaryFile =
                Files.createTempFile(
                        destination.getParent(), destination.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporaryFile, content, StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporaryFile,
                        destination,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("El JDK no proporciona SHA-256", exception);
        }
    }

    private record CorpusArticle(String id, String title, String abstractText) {}

    private record ExportedText(String id, String text) {}

    private static final class CorpusJsonParser {

        private final String json;
        private int indice;

        private CorpusJsonParser(String json) {
            this.json = json;
        }

        private List<CorpusArticle> parse() throws IOException {
            skipWhitespace();
            expect('[');
            List<CorpusArticle> articles = new ArrayList<>();
            skipWhitespace();
            if (consume(']')) {
                ensureEnd();
                return List.copyOf(articles);
            }
            while (true) {
                articles.add(parseArticle());
                skipWhitespace();
                if (consume(']')) {
                    break;
                }
                expect(',');
            }
            ensureEnd();
            return List.copyOf(articles);
        }

        private CorpusArticle parseArticle() throws IOException {
            skipWhitespace();
            expect('{');
            String id = null;
            String title = null;
            String abstractText = null;
            skipWhitespace();
            if (consume('}')) {
                throw error("El artículo no contiene campos requeridos");
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                if ("id".equals(key)) {
                    id = parseRequiredString(key);
                } else if ("title".equals(key)) {
                    title = parseRequiredString(key);
                } else if ("abstractText".equals(key)) {
                    abstractText = parseRequiredString(key);
                } else {
                    skipValue();
                }
                skipWhitespace();
                if (consume('}')) {
                    break;
                }
                expect(',');
            }
            if (id == null || title == null || abstractText == null) {
                throw error("Cada artículo debe tener id, title y abstractText como cadenas");
            }
            return new CorpusArticle(id, title, abstractText);
        }

        private String parseRequiredString(String field) throws IOException {
            if (indice >= json.length() || json.charAt(indice) != '"') {
                throw error("El campo " + field + " debe ser una cadena JSON");
            }
            return parseString();
        }

        private String parseString() throws IOException {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (indice < json.length()) {
                char current = json.charAt(indice++);
                if (current == '"') {
                    return value.toString();
                }
                if (current < 0x20) {
                    throw error("Carácter de control no escapado en cadena JSON");
                }
                if (current != '\\') {
                    value.append(current);
                    continue;
                }
                if (indice >= json.length()) {
                    throw error("Escape JSON incompleto");
                }
                char escape = json.charAt(indice++);
                switch (escape) {
                    case '"', '\\', '/' -> value.append(escape);
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> value.append(parseUnicodeEscape());
                    default -> throw error("Escape JSON inválido");
                }
            }
            throw error("Cadena JSON sin cerrar");
        }

        private char parseUnicodeEscape() throws IOException {
            if (indice + 4 > json.length()) {
                throw error("Escape Unicode incompleto");
            }
            int value = 0;
            for (int contador = 0; contador < 4; contador++) {
                int digit = Character.digit(json.charAt(indice++), 16);
                if (digit < 0) {
                    throw error("Dígito hexadecimal inválido en escape Unicode");
                }
                value = value * 16 + digit;
            }
            return (char) value;
        }

        private void skipValue() throws IOException {
            skipWhitespace();
            if (indice >= json.length()) {
                throw error("Valor JSON faltante");
            }
            char current = json.charAt(indice);
            if (current == '"') {
                parseString();
            } else if (current == '{') {
                skipObject();
            } else if (current == '[') {
                skipArray();
            } else {
                skipLiteralOrNumber();
            }
        }

        private void skipObject() throws IOException {
            expect('{');
            skipWhitespace();
            if (consume('}')) {
                return;
            }
            while (true) {
                skipWhitespace();
                parseString();
                skipWhitespace();
                expect(':');
                skipValue();
                skipWhitespace();
                if (consume('}')) {
                    return;
                }
                expect(',');
            }
        }

        private void skipArray() throws IOException {
            expect('[');
            skipWhitespace();
            if (consume(']')) {
                return;
            }
            while (true) {
                skipValue();
                skipWhitespace();
                if (consume(']')) {
                    return;
                }
                expect(',');
            }
        }

        private void skipLiteralOrNumber() throws IOException {
            int start = indice;
            while (indice < json.length()) {
                char current = json.charAt(indice);
                if (current == ','
                        || current == ']'
                        || current == '}'
                        || isJsonWhitespace(current)) {
                    break;
                }
                indice++;
            }
            if (start == indice) {
                throw error("Valor JSON inválido");
            }
            String literal = json.substring(start, indice);
            if (!"true".equals(literal)
                    && !"false".equals(literal)
                    && !"null".equals(literal)
                    && !isJsonNumber(literal)) {
                throw error("Literal JSON inválido");
            }
        }

        private boolean isJsonNumber(String value) {
            int cursor = 0;
            if (value.isEmpty()) {
                return false;
            }
            if (value.charAt(cursor) == '-') {
                cursor++;
            }
            if (cursor >= value.length()) {
                return false;
            }
            if (value.charAt(cursor) == '0') {
                cursor++;
            } else {
                int digitsStart = cursor;
                while (cursor < value.length() && isDigit(value.charAt(cursor))) {
                    cursor++;
                }
                if (cursor == digitsStart) {
                    return false;
                }
            }
            if (cursor < value.length() && value.charAt(cursor) == '.') {
                cursor++;
                int fractionStart = cursor;
                while (cursor < value.length() && isDigit(value.charAt(cursor))) {
                    cursor++;
                }
                if (cursor == fractionStart) {
                    return false;
                }
            }
            if (cursor < value.length()
                    && (value.charAt(cursor) == 'e' || value.charAt(cursor) == 'E')) {
                cursor++;
                if (cursor < value.length()
                        && (value.charAt(cursor) == '+' || value.charAt(cursor) == '-')) {
                    cursor++;
                }
                int exponentStart = cursor;
                while (cursor < value.length() && isDigit(value.charAt(cursor))) {
                    cursor++;
                }
                if (cursor == exponentStart) {
                    return false;
                }
            }
            return cursor == value.length();
        }

        private boolean isDigit(char character) {
            return character >= '0' && character <= '9';
        }

        private void ensureEnd() throws IOException {
            skipWhitespace();
            if (indice != json.length()) {
                throw error("Contenido adicional después del arreglo JSON");
            }
        }

        private void skipWhitespace() {
            while (indice < json.length() && isJsonWhitespace(json.charAt(indice))) {
                indice++;
            }
        }

        private boolean isJsonWhitespace(char character) {
            return character == ' ' || character == '\t' || character == '\n' || character == '\r';
        }

        private boolean consume(char expected) {
            if (indice < json.length() && json.charAt(indice) == expected) {
                indice++;
                return true;
            }
            return false;
        }

        private void expect(char expected) throws IOException {
            if (!consume(expected)) {
                throw error("Se esperaba '" + expected + "'");
            }
        }

        private IOException error(String message) {
            return new IOException(message + " en la posición " + indice);
        }
    }
}
