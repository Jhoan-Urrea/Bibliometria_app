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
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
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
  private static final Pattern ABSTRACT_HEADING =
      Pattern.compile(
          "(?im)(?:^|[\\s.])(?:\\d+(?:\\.\\d+)*\\s*)?a\\s*b\\s*s\\s*t\\s*r\\s*a\\s*c\\s*t\\b"
              + "\\s*[:.—-]?\\s*");
  private static final Pattern ABSTRACT_END =
      Pattern.compile("(?im)(?:^|[\\s.])(?:\\d+(?:\\.\\d+)*\\s*)?introduction\\b");
  private static final Pattern NON_TITLE_LINE =
      Pattern.compile(
          "(?i)(\\bdoi\\b|\\bhttps?://|\\bwww\\.|@|copyright|all rights reserved|"
              + "\\bvol(?:ume)?\\.?\\s+\\d|\\bissn\\b|\\breceived\\b|\\baccepted\\b)");
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

    System.out.printf(
        "Corpus generado: %d registros en %s%n",
        records.size(), outputPath.toAbsolutePath().normalize());
  }

  /**
   * Valida los PDF indicados en el manifiesto y crea el corpus JSON.
   *
   * @param rawDirectory directorio que contiene los PDF
   * @param manifestPath manifiesto CSV con columnas id,nombre_archivo,sha256
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

    List<CorpusRecord> records = new ArrayList<>(EXPECTED_PDF_COUNT);
    Path normalizedRawDirectory = rawDirectory.toAbsolutePath().normalize();
    for (ManifestEntry entry : entries) {
      Path pdfPath = normalizedRawDirectory.resolve(entry.fileName()).normalize();
      if (!pdfPath.getParent().equals(normalizedRawDirectory) || !Files.isRegularFile(pdfPath)) {
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

      records.add(extract(entry.id(), pdfPath));
    }

    writeAtomically(outputPath, records);
    return List.copyOf(records);
  }

  private List<ManifestEntry> readManifest(Path manifestPath) throws IOException {
    List<ManifestEntry> entries = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    Set<String> fileNames = new HashSet<>();

    try (BufferedReader reader = Files.newBufferedReader(manifestPath, StandardCharsets.UTF_8)) {
      String header = reader.readLine();
      if (header == null
          || !parseCsvLine(header).equals(List.of("id", "nombre_archivo", "sha256"))) {
        throw new IOException("El manifiesto debe iniciar con id,nombre_archivo,sha256");
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
          throw new IOException("ID o nombre de archivo duplicado en la línea " + lineNumber);
        }
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")
            || !hash.matches("(?i)[0-9a-f]{64}")) {
          throw new IOException("Nombre PDF o SHA-256 inválido en la línea " + lineNumber);
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

  private CorpusRecord extract(String id, Path pdfPath) throws IOException {
    String text;
    try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);
      text = stripper.getText(document).replace("\r\n", "\n").replace('\r', '\n');
    }

    String abstractText = extractAbstract(text);
    String title = extractTitle(text, pdfPath);
    String authors = extractAuthors(text, title);
    return new CorpusRecord(id, title, authors, abstractText, false);
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
    return normalizeWhitespace(text.substring(start, end));
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
            .filter(
                line ->
                    !line.matches("(?i)^\\s*(open access|research article|review article)\\s*$"))
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
        titlePosition >= 0 ? preAbstract.substring(titlePosition + title.length()) : preAbstract;

    for (String line :
        afterTitle.lines().map(String::strip).filter(s -> !s.isBlank()).limit(12).toList()) {
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
    return line.contains(",")
        || line.matches(".*\\b(and|&|et al\\.)\\b.*")
        || line.matches(".*[¹²³⁴⁵⁶⁷⁸⁹*].*");
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
      String id, String title, String authors, String abstractText, boolean verified) {}
}
