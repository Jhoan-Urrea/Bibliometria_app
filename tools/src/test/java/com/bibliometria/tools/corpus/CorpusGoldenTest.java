package com.bibliometria.tools.corpus;

import static org.assertj.core.api.Assertions.assertThat;

import com.bibliometria.core.text.Article;
import com.bibliometria.core.text.ProcessedDocument;
import com.bibliometria.core.text.TextPreprocessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CorpusGoldenTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void preprocessingMatchesGoldenOutputsForAllTwentyAbstracts() throws IOException {
        JsonNode corpus = readResource("/golden/corpus.json");
        TextPreprocessor preprocessor = new TextPreprocessor();

        assertThat(corpus.size()).isEqualTo(20);
        for (JsonNode article : corpus) {
            String id = article.path("id").asText();
            ProcessedDocument processed =
                    preprocessor.process(
                            new Article(
                                    id,
                                    article.path("title").asText(),
                                    article.path("authors").asText(),
                                    article.path("abstractText").asText(),
                                    article.path("verified").asBoolean()));
            JsonNode golden = readResource("/golden/" + id + ".json");

            assertThat(processed.textoNormalizado())
                    .isEqualTo(golden.path("textoNormalizado").asText());
            assertThat(processed.tokens()).isEqualTo(readStringArray(golden.path("tokens")));
            assertThat(new ArrayList<>(processed.conjunto()))
                    .isEqualTo(readStringArray(golden.path("conjunto")));
            JsonNode frequencies = objectMapper.valueToTree(processed.frecuencias());
            assertThat(frequencies).isEqualTo(golden.path("frecuencias"));
            assertThat(processed.cadenaCanonica())
                    .isEqualTo(golden.path("cadenaCanonica").asText());
        }
    }

    private JsonNode readResource(String resource) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Golden resource not found: " + resource);
            }
            return objectMapper.readTree(input);
        }
    }

    private List<String> readStringArray(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }
}
