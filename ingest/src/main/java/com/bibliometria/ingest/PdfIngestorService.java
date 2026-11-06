package com.bibliometria.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Materializa una única extracción reproducible del corpus bibliográfico.
 *
 * <p>La extracción se mantiene en este módulo para evitar reprocesar PDF en caliente en las capas
 * de consulta y para que los resultados puedan revisarse y reproducirse a partir de los archivos
 * identificados por su SHA-256 en el manifiesto.
 */
public class PdfIngestorService {

    private static final int EXPECTED_PDF_COUNT = 20;
    private static final System.Logger LOGGER =
            System.getLogger(PdfIngestorService.class.getName());
    private static final String WORKING_LANGUAGE = "en";
    private static final String PENDING_REVIEWER = "PENDIENTE_REVISION_HUMANA";
    private static final Pattern ABSTRACT_HEADING =
            Pattern.compile(
                    "(?im)(?:^|[\\s.])(?:\\d+(?:\\.\\d+)*\\s*)?a\\s*b\\s*s\\s*t\\s*r\\s*a\\s*c\\s*t\\b"
                            + "\\s*[:.—-]?\\s*");
    private static final Pattern ABSTRACT_END =
            Pattern.compile("(?im)(?:^|[\\s.])(?:\\d+(?:\\.\\d+)*\\s*)?introduction\\b");
    private static final Pattern NON_TITLE_LINE =
            Pattern.compile(
                    "(?i)(\\bdoi\\b|\\bhttps?://|\\bwww\\.|@|copyright|all rights reserved|"
                            + "\\bvol(?:ume)?\\.?\\s+\\d|\\bissn\\b|\\breceived\\b|\\baccepted\\b|"
                            + "contents lists|science direct|open access|research article|review article)");
    private static final Pattern AUTHOR_AFFILIATION =
            Pattern.compile(
                    "(?i)(@|\\b(university|universidad|department|faculty|school|institute|"
                            + "corresponding author|received|accepted)\\b)");

    private final ObjectMapper objectMapper =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * Extrae el corpus desde el directorio de trabajo del proyecto.
     *
     * <p>Ejecutar desde la raíz del repositorio para usar {@code data/raw/}, {@code
     * data/manifest.csv} y {@code data/corpus.json}.
     *
     * @param args no se admiten argumentos
     * @throws IOException si falla la lectura, validación o escritura del corpus
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 0) {
            throw new IllegalArgumentException("Este comando no admite argumentos");
        }

        Path rawDirectory = Path.of("data", "raw");
        Path manifestPath = Path.of("data", "manifest.csv");
        Path outputPath = Path.of("data", "corpus.json");
        List<CorpusRecord> records =
                new PdfIngestorService().ingest(rawDirectory, manifestPath, outputPath);
        Path apiCorpusPath = Path.of("api", "src", "main", "resources", "data", "corpus.json");
        syncFrozenCorpus(
                outputPath, apiCorpusPath, Path.of("docs", "evidence", "f1", "corpus.sha256"));

        System.out.printf(
                "Corpus generado y sincronizado: %d registros en %s y %s%n",
                records.size(),
                outputPath.toAbsolutePath().normalize(),
                apiCorpusPath.toAbsolutePath().normalize());
    }

    private static void syncFrozenCorpus(Path generated, Path apiCopy, Path hashFile)
            throws IOException {
        Path absoluteApiCopy = apiCopy.toAbsolutePath().normalize();
        Path absoluteHashFile = hashFile.toAbsolutePath().normalize();
        Files.createDirectories(absoluteApiCopy.getParent());
        Files.createDirectories(absoluteHashFile.getParent());
        Path apiTemporary = Files.createTempFile(absoluteApiCopy.getParent(), "corpus-", ".tmp");
        try {
            Files.copy(generated, apiTemporary, StandardCopyOption.REPLACE_EXISTING);
            moveAtomically(apiTemporary, absoluteApiCopy);
        } finally {
            Files.deleteIfExists(apiTemporary);
        }
        String corpusHash = new PdfIngestorService().sha256(generated.toAbsolutePath().normalize());
        Path hashTemporary =
                Files.createTempFile(absoluteHashFile.getParent(), "corpus-hash-", ".tmp");
        try {
            Files.writeString(
                    hashTemporary, corpusHash + System.lineSeparator(), StandardCharsets.UTF_8);
            moveAtomically(hashTemporary, absoluteHashFile);
        } finally {
            Files.deleteIfExists(hashTemporary);
        }
    }

    private static void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(
                    source,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Valida los PDF indicados en el manifiesto y crea el corpus JSON.
     *
     * @param rawDirectory directorio que contiene los PDF
     * @param manifestPath manifiesto CSV con columnas id,filename,sha256
     * @param outputPath destino del corpus JSON
     * @return los 20 registros extraídos, en el orden del manifiesto
     * @throws IOException si hay un error de lectura, validación o escritura
     */
    public List<CorpusRecord> ingest(Path rawDirectory, Path manifestPath, Path outputPath)
            throws IOException {
        List<ManifestEntry> entries = readManifest(manifestPath);
        if (entries.size() != EXPECTED_PDF_COUNT) {
            throw new IOException(
                    "El manifiesto debe contener exactamente "
                            + EXPECTED_PDF_COUNT
                            + " PDF; contiene "
                            + entries.size());
        }

        Map<String, CorpusRecord> verifiedRecords = readVerifiedRecords(outputPath);
        List<CorpusRecord> records = new ArrayList<>(EXPECTED_PDF_COUNT);
        Path normalizedRawDirectory = rawDirectory.toAbsolutePath().normalize();
        for (ManifestEntry entry : entries) {
            Path pdfPath = normalizedRawDirectory.resolve(entry.fileName()).normalize();
            if (!pdfPath.getParent().equals(normalizedRawDirectory)
                    || !Files.isRegularFile(pdfPath)) {
                throw new IOException(
                        "PDF inexistente o ruta inválida en el manifiesto: " + entry.fileName());
            }

            String actualHash = sha256(pdfPath);
            if (!actualHash.equalsIgnoreCase(entry.sha256())) {
                throw new IOException(
                        "SHA-256 no coincide para "
                                + entry.fileName()
                                + ": esperado "
                                + entry.sha256()
                                + ", obtenido "
                                + actualHash);
            }

            CorpusRecord verified = verifiedRecords.get(entry.id());
            if (verified != null
                    && verified.filename().equals(entry.fileName())
                    && verified.sha256().equalsIgnoreCase(actualHash)) {
                records.add(verified);
            } else {
                records.add(extract(entry, pdfPath));
            }
        }

        writeAtomically(outputPath, records);
        return List.copyOf(records);
    }

    private Map<String, CorpusRecord> readVerifiedRecords(Path corpusPath) throws IOException {
        if (!Files.isRegularFile(corpusPath)) {
            return Map.of();
        }

        Map<String, CorpusRecord> verifiedRecords = new HashMap<>();
        for (CorpusRecord record :
                objectMapper.readValue(corpusPath.toFile(), CorpusRecord[].class)) {
            if (record.verified()
                    && !PENDING_REVIEWER.equals(record.verificadoPor())
                    && record.notas() != null
                    && !record.notas().contains("AWAITING_MANUAL_DOUBLE_REVIEW")) {
                verifiedRecords.put(record.id(), record);
            }
        }
        return verifiedRecords;
    }

    private List<ManifestEntry> readManifest(Path manifestPath) throws IOException {
        List<ManifestEntry> entries = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> fileNames = new HashSet<>();

        try (BufferedReader reader =
                Files.newBufferedReader(manifestPath, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null
                    || !parseCsvLine(header).equals(List.of("id", "filename", "sha256"))) {
                throw new IOException("El manifiesto debe iniciar con id,filename,sha256");
            }

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }

                List<String> fields = parseCsvLine(line);
                if (fields.size() < 3) {
                    throw new IOException("Fila CSV inválida en la línea " + lineNumber);
                }

                String id = fields.get(0).trim();
                String fileName = String.join(",", fields.subList(1, fields.size() - 1)).trim();
                String hash = fields.getLast().trim();
                if (id.isBlank() || fileName.isBlank() || hash.isBlank()) {
                    throw new IOException("Fila CSV inválida en la línea " + lineNumber);
                }
                if (!ids.add(id) || !fileNames.add(fileName)) {
                    throw new IOException(
                            "ID o nombre de archivo duplicado en la línea " + lineNumber);
                }
                if (!fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")
                        || !hash.matches("(?i)[0-9a-f]{64}")) {
                    throw new IOException(
                            "Nombre PDF o SHA-256 inválido en la línea " + lineNumber);
                }
                entries.add(new ManifestEntry(id, fileName, hash));
            }
        }
        return entries;
    }

    private List<String> parseCsvLine(String line) throws IOException {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(current);
            }
        }
        if (quoted) {
            throw new IOException("Campo CSV con comillas sin cerrar");
        }
        fields.add(field.toString());
        return fields;
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible en esta JVM", exception);
        }
    }

    private CorpusRecord extract(ManifestEntry entry, Path pdfPath) throws IOException {
        List<String> pageTexts = new ArrayList<>();
        String metadataTitle;
        String metadataAuthors;
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            PDDocumentInformation information = document.getDocumentInformation();
            metadataTitle = information.getTitle();
            metadataAuthors = information.getAuthor();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pageTexts.add(stripper.getText(document).replace("\r\n", "\n").replace('\r', '\n'));
            }
        }

        String text = String.join("\n", pageTexts);
        int abstractPage = findAbstractPage(pageTexts);
        List<String> warnings = new ArrayList<>();
        String abstractSource = abstractPage == 0 ? text : pageTexts.get(abstractPage - 1);
        String abstractText = extractAbstract(abstractSource);
        if (abstractText.isBlank()) {
            warnings.add("ABSTRACT_NOT_FOUND");
        } else if (abstractText.length() < 100) {
            warnings.add("ABSTRACT_UNDER_100_CHARACTERS");
        } else if (abstractText.length() > 3_000) {
            warnings.add("ABSTRACT_IMPLAUSIBLY_LONG_REQUIRES_MANUAL_REVIEW");
        }
        if (abstractText.matches(
                "(?is)^\\s*(?:\\d+\\s*)?(?:faculty|department|university|univeristy)\\b.*")) {
            warnings.add("ABSTRACT_START_REQUIRES_MANUAL_REVIEW");
        }

        String title =
                isPlausibleTitle(metadataTitle)
                        ? normalizeWhitespace(metadataTitle)
                        : titleFromFileName(pdfPath);
        if (title.isBlank() || NON_TITLE_LINE.matcher(title).find()) {
            warnings.add("TITLE_REQUIRES_MANUAL_REVIEW");
        }

        String authors =
                isPlausibleAuthor(metadataAuthors)
                        ? normalizeWhitespace(metadataAuthors)
                        : normalizeWhitespace(extractAuthors(text, title))
                                .replaceAll("\\s+\\d+$", "");
        if (authors.isBlank()) {
            warnings.add("AUTHORS_NOT_FOUND");
            authors = "PENDIENTE_REVISION_MANUAL";
        }
        if (abstractPage == 0) {
            warnings.add("ABSTRACT_PAGE_NOT_FOUND");
        }
        warnings.add("AWAITING_MANUAL_DOUBLE_REVIEW");
        for (String warning : warnings) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "Corpus record {0} ({1}): {2}",
                    entry.id(),
                    entry.fileName(),
                    warning);
        }

        return new CorpusRecord(
                entry.id(),
                entry.fileName(),
                entry.sha256().toLowerCase(Locale.ROOT),
                title,
                authors,
                abstractText,
                WORKING_LANGUAGE,
                abstractPage == 0 ? null : abstractPage,
                false,
                PENDING_REVIEWER,
                List.copyOf(warnings));
    }

    private int findAbstractPage(List<String> pageTexts) {
        for (int index = 0; index < pageTexts.size(); index++) {
            if (ABSTRACT_HEADING.matcher(pageTexts.get(index)).find()) {
                return index + 1;
            }
        }
        return 0;
    }

    private boolean isPlausibleTitle(String value) {
        return value != null
                && value.strip().length() >= 10
                && !value.matches(
                        "(?i).*(^|[^a-z])(proof|contents lists|science direct)([^a-z]|$).*")
                && !NON_TITLE_LINE.matcher(value).find();
    }

    private boolean isPlausibleAuthor(String value) {
        return value != null
                && !value.isBlank()
                && !value.contains("@")
                && !AUTHOR_AFFILIATION.matcher(value).find()
                && value.length() <= 300;
    }

    private String extractAbstract(String text) {
        Matcher heading = ABSTRACT_HEADING.matcher(text);
        if (!heading.find()) {
            return "";
        }

        int start = heading.end();
        Matcher ending = ABSTRACT_END.matcher(text);
        ending.region(start, text.length());
        int end = ending.find() ? ending.start() : text.length();
        String abstractText = normalizeWhitespace(text.substring(start, end));
        abstractText =
                abstractText.replaceFirst(
                        "(?i)^(?:keywords?\\s*[:.]?\\s*)?(?:\\d{1,2}\\s+[a-z]+\\s+\\d{4}\\s+)+",
                        "");
        return abstractText.replaceFirst("(?i)^keywords?\\s*[:.]?\\s*", "").strip();
    }

    private String extractTitle(String text, Path pdfPath) {
        int abstractStart = text.length();
        Matcher heading = ABSTRACT_HEADING.matcher(text);
        if (heading.find()) {
            abstractStart = heading.start();
        }

        List<String> candidates =
                text.substring(0, abstractStart)
                        .lines()
                        .map(String::strip)
                        .filter(line -> !line.isBlank() && line.length() >= 5)
                        .filter(line -> !NON_TITLE_LINE.matcher(line).find())
                        .filter(line -> !NON_TITLE_LINE.matcher(line).find())
                        .toList();

        if (candidates.isEmpty()) {
            return titleFromFileName(pdfPath);
        }

        StringBuilder title = new StringBuilder(candidates.getFirst());
        for (int i = 1; i < Math.min(candidates.size(), 3); i++) {
            String candidate = candidates.get(i);
            if (AUTHOR_AFFILIATION.matcher(candidate).find() || looksLikeAuthors(candidate)) {
                break;
            }
            if (candidate.length() > 140 || candidate.matches(".*[.!?]$")) {
                break;
            }
            title.append(' ').append(candidate);
        }
        return normalizeWhitespace(title.toString());
    }

    private String extractAuthors(String text, String title) {
        int abstractStart = text.length();
        Matcher abstractHeading = ABSTRACT_HEADING.matcher(text);
        if (abstractHeading.find()) {
            abstractStart = abstractHeading.start();
        }

        String preAbstract = text.substring(0, abstractStart);
        int titlePosition = preAbstract.indexOf(title);
        String afterTitle =
                titlePosition >= 0
                        ? preAbstract.substring(titlePosition + title.length())
                        : preAbstract;

        for (String line :
                afterTitle
                        .lines()
                        .map(String::strip)
                        .filter(s -> !s.isBlank())
                        .limit(30)
                        .toList()) {
            if (AUTHOR_AFFILIATION.matcher(line).find()) {
                continue;
            }
            if (line.length() <= 180 && looksLikeAuthors(line)) {
                return normalizeWhitespace(line);
            }
        }
        return "";
    }

    private boolean looksLikeAuthors(String line) {
        String normalized = line.strip();
        if (normalized.isEmpty()
                || normalized.matches("(?i)^(and|or|the|of|for|in)\\b.*")
                || NON_TITLE_LINE.matcher(normalized).find()) {
            return false;
        }
        normalized = normalized.replaceAll("(?<=\\p{L})\\d+(?:,\\d+)*", "");
        String[] people = normalized.split("(?i)\\s+(?:and|&)\\s+|\\s*\\|\\s*|,\\s*(?=[A-Z])");
        if (people.length < 2) {
            return false;
        }
        for (String person : people) {
            String name = person.replaceAll("[¹²³⁴⁵⁶⁷⁸⁹*\\d]", "").strip();
            if (name.split("\\s+").length < 2
                    || !Character.isUpperCase(name.codePointAt(0))
                    || name.toLowerCase(Locale.ROOT)
                            .matches(".*\\b(the|current|issue|journal)\\b.*")) {
                return false;
            }
        }
        return true;
    }

    private String titleFromFileName(Path pdfPath) {
        String fileName = pdfPath.getFileName().toString().replaceFirst("(?i)\\.pdf$", "");
        return fileName.replaceFirst("^\\s*\\d+\\.\\s*", "").replaceAll("\\s+", " ").strip();
    }

    private String normalizeWhitespace(String value) {
        return value.replaceAll("\\s+", " ").strip();
    }

    private void writeAtomically(Path outputPath, List<CorpusRecord> records) throws IOException {
        Path absoluteOutput = outputPath.toAbsolutePath().normalize();
        Path parent = absoluteOutput.getParent();
        if (parent == null) {
            throw new IOException("La ruta de salida no tiene directorio padre: " + outputPath);
        }
        Files.createDirectories(parent);

        Path temporaryFile = Files.createTempFile(parent, "corpus-", ".json.tmp");
        try {
            objectMapper.writeValue(temporaryFile.toFile(), records);
            try {
                Files.move(
                        temporaryFile,
                        absoluteOutput,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, absoluteOutput, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private record ManifestEntry(String id, String fileName, String sha256) {}

    /** Registro serializado del corpus. */
    public record CorpusRecord(
            String id,
            String filename,
            String sha256,
            String title,
            String authors,
            String abstractText,
            String language,
            Integer paginaAbstract,
            boolean verified,
            String verificadoPor,
            List<String> notas) {}
}
