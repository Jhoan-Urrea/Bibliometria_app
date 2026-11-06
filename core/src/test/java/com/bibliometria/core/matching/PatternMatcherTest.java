package com.bibliometria.core.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PatternMatcherTest {

    private static final List<StringMatcher> MATCHERS =
            List.of(new KmpMatcher(), new BoyerMooreMatcher(), new RabinKarpMatcher());

    @Test
    void buildsExpectedLpsVector() {
        assertThat(KmpMatcher.construirLps("ababaca")).containsExactly(0, 0, 1, 2, 3, 0, 1);
    }

    @Test
    void buildsExpectedStrongGoodSuffixVectorAndOccurrenceShift() {
        assertThat(BoyerMooreMatcher.buenSufijo("ababaca")).containsExactly(6, 6, 6, 6, 2, 1);
        assertThat(BoyerMooreMatcher.desplazamientoTrasOcurrencia("ababaca")).isEqualTo(6);
    }

    @Test
    void boyerMooreShiftsAreAlwaysPositive() {
        for (String patron : List.of("a", "aaaa", "ababaca", "abcab")) {
            assertThat(BoyerMooreMatcher.desplazamientoTrasOcurrencia(patron)).isPositive();
            assertThat(
                            java.util.Arrays.stream(BoyerMooreMatcher.buenSufijo(patron))
                                    .allMatch(desplazamiento -> desplazamiento > 0))
                    .isTrue();
        }
    }

    @Test
    void recordsTheLastPatternOccurrenceOfEachCharacter() {
        int[] ultimaAparicion = BoyerMooreMatcher.ultimaAparicion("abac");

        assertThat(ultimaAparicion['a']).isEqualTo(2);
        assertThat(ultimaAparicion['b']).isEqualTo(1);
        assertThat(ultimaAparicion['c']).isEqualTo(3);
        assertThat(ultimaAparicion['z']).isEqualTo(-1);
    }

    @ParameterizedTest
    @MethodSource("matchingCases")
    void allMatchersAgreeWithTheOracle(String texto, String patron, List<Integer> expected) {
        MatchResult oracle = new NaiveMatcher().buscarTodas(texto, patron);

        assertThat(oracle.posiciones()).isEqualTo(expected);
        for (StringMatcher matcher : MATCHERS) {
            assertThat(matcher.buscarTodas(texto, patron).posiciones())
                    .as(
                            "%s para texto <%s> y patrón <%s>",
                            matcher.getClass().getSimpleName(), texto, patron)
                    .isEqualTo(oracle.posiciones());
        }
    }

    private static Stream<Arguments> matchingCases() {
        return Stream.of(
                Arguments.of("AAAA", "AA", List.of(0, 1, 2)),
                Arguments.of("abababaca", "ababa", List.of(0, 2)),
                Arguments.of("abc", "abcd", List.of()),
                Arguments.of("café café", "café", List.of(0, 5)),
                Arguments.of("La bibliometría", "metría", List.of(9)),
                Arguments.of("😀😀", "😀", List.of(0, 2)),
                Arguments.of("", "", List.of(0)),
                Arguments.of("abc", "", List.of(0, 1, 2, 3)),
                Arguments.of("", "a", List.of()));
    }

    @Test
    void rabinKarpReportsHashMatchesAndFalsePositives() {
        MatchResult result = new RabinKarpMatcher().buscarTodas("AAAA", "AA");

        assertThat(result.posiciones()).containsExactly(0, 1, 2);
        assertThat(result.extras())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("hashCoincidencias", 3L, "falsosPositivos", 0L));
        assertThat(result.comparaciones()).isEqualTo(6);
    }

    @Test
    void rabinKarpVerifiesCharacterByCharacterWhenHashesCollide() {
        MatchResult result = new RabinKarpMatcher().buscarTodas("a\u0102", "b\u0001");

        assertThat(result.posiciones()).isEmpty();
        assertThat(result.comparaciones()).isEqualTo(1);
        assertThat(result.extras())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("hashCoincidencias", 1L, "falsosPositivos", 1L));
    }

    @Test
    void countsEachTextPatternCharacterEqualityEvaluation() {
        MatchResult result = new NaiveMatcher().buscarTodas("abac", "ac");

        assertThat(result.posiciones()).containsExactly(2);
        assertThat(result.comparaciones()).isEqualTo(5);
    }

    @Test
    void rejectsNullInputs() {
        assertThatThrownBy(() -> new KmpMatcher().buscarTodas(null, "a"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BoyerMooreMatcher().buscarTodas("a", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RabinKarpMatcher().buscarTodas(null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void agreesWithTheOracleForDeterministicRandomInputs() {
        Random random = new Random(20261102);
        char[] alphabet = {'a', 'b', 'c', 'ñ', 'á', '\uD83D', '\uDE00'};
        NaiveMatcher oracle = new NaiveMatcher();

        for (int caso = 0; caso < 500; caso++) {
            String texto = randomString(random, alphabet, random.nextInt(20));
            String patron = randomString(random, alphabet, random.nextInt(8));
            List<Integer> expected = oracle.buscarTodas(texto, patron).posiciones();

            for (StringMatcher matcher : MATCHERS) {
                assertThat(matcher.buscarTodas(texto, patron).posiciones())
                        .as("caso %s con %s", caso, matcher.getClass().getSimpleName())
                        .isEqualTo(expected);
            }
        }
    }

    private static String randomString(Random random, char[] alphabet, int length) {
        StringBuilder result = new StringBuilder(length);
        for (int indice = 0; indice < length; indice++) {
            result.append(alphabet[random.nextInt(alphabet.length)]);
        }
        return result.toString();
    }
}
