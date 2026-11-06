package com.bibliometria.core.text;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Carga stopwords desde streams para mantener el preprocesador desacoplado del sistema de archivos.
 */
public final class StopwordLoader {

    private static final String DEFAULT_RESOURCE = "/stopwords.txt";

    private StopwordLoader() {}

    public static Set<String> load(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input");
        Set<String> stopwords = new LinkedHashSet<>();
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            String normalized =
                    Normalizer.normalize(line, Normalizer.Form.NFKC)
                            .toLowerCase(Locale.ROOT)
                            .strip();
            if (!normalized.isEmpty()) {
                stopwords.add(normalized);
            }
        }
        return Set.copyOf(stopwords);
    }

    public static Set<String> loadDefault() throws IOException {
        try (InputStream input = StopwordLoader.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (input == null) {
                throw new IOException("No se encontró el recurso classpath " + DEFAULT_RESOURCE);
            }
            return load(input);
        }
    }
}
