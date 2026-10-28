// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.assessment;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpanishNumberNormalizerTests {
    private static final String[] ZERO_TO_TWENTY_NINE = {
            "cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
            "diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete",
            "dieciocho", "diecinueve", "veinte", "veintiuno", "veintidós", "veintitrés", "veinticuatro",
            "veinticinco", "veintiséis", "veintisiete", "veintiocho", "veintinueve"};
    private static final String[] TENS = {"", "", "", "treinta", "cuarenta", "cincuenta", "sesenta",
            "setenta", "ochenta", "noventa"};

    private static String spoken(int value) {
        if (value < 30) return ZERO_TO_TWENTY_NINE[value];
        if (value < 100) return TENS[value / 10] + (value % 10 == 0 ? "" : " y " + spoken(value % 10));
        if (value == 100) return "cien";
        return "ciento " + spoken(value - 100);
    }

    @Test
    void recognizesEveryValueAsDigitsAndSpanishWords() {
        for (int value = 0; value <= 120; value++) {
            for (String text : List.of(Integer.toString(value), spoken(value))) {
                assertThat(SpanishNumberNormalizer.normalize(text).numbers())
                        .as("%s", text).containsExactly(new SpanishNumberNormalizer.NumberMention(value, 0, text.length()));
            }
        }
    }

    @Test
    void preservesOriginalOffsetsOrderAndRepetitions() {
        String text = "😀 UN bloque, DIECISEIS; treinta y UNA, 6 y seis.";
        var result = SpanishNumberNormalizer.normalize(text);
        assertThat(result.numbers()).extracting(SpanishNumberNormalizer.NumberMention::value)
                .containsExactly(1, 16, 31, 6, 6);
        assertThat(result.numbers().stream().map(n -> text.substring(n.start(), n.end())).toList())
                .containsExactly("UN", "DIECISEIS", "treinta y UNA", "6", "seis");
        assertThat(result.cues()).isEmpty();
        assertThatThrownBy(() -> result.numbers().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.cues().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void recognizesGenderAccentAndDecomposedUnicodeVariants() {
        for (String word : List.of("un", "uno", "una", "UN")) {
            assertThat(SpanishNumberNormalizer.normalize(word).numbers().getFirst().value()).isEqualTo(1);
        }
        for (String word : List.of("veintiún", "veintiun", "veintiuna", "VEINTIU\u0301N")) {
            var match = SpanishNumberNormalizer.normalize(word).numbers().getFirst();
            assertThat(match.value()).isEqualTo(21);
            assertThat(word.substring(match.start(), match.end())).isEqualTo(word);
        }
    }

    @Test
    void marksExplicitCuesWithoutAssigningTheirScope() {
        String text = "No son seis, quizá siete; tal vez no, jamás, tampoco, posiblemente y probablemente.";
        var result = SpanishNumberNormalizer.normalize(text);
        assertThat(result.numbers()).extracting(SpanishNumberNormalizer.NumberMention::value).containsExactly(6, 7);
        assertThat(result.cues().stream().map(c -> text.substring(c.start(), c.end())).toList())
                .containsExactly("No", "quizá", "tal vez", "no", "jamás", "tampoco", "posiblemente", "probablemente");
        assertThat(result.cues()).extracting(SpanishNumberNormalizer.Cue::kind).containsExactly(
                SpanishNumberNormalizer.CueKind.NEGATION, SpanishNumberNormalizer.CueKind.UNCERTAINTY,
                SpanishNumberNormalizer.CueKind.UNCERTAINTY, SpanishNumberNormalizer.CueKind.NEGATION,
                SpanishNumberNormalizer.CueKind.NEGATION, SpanishNumberNormalizer.CueKind.NEGATION,
                SpanishNumberNormalizer.CueKind.UNCERTAINTY, SpanishNumberNormalizer.CueKind.UNCERTAINTY);
    }

    @Test
    void preservesListsAfterHundredsAndRedactsNumericValues() {
        var result = SpanishNumberNormalizer.normalize("cien y seis, ciento uno y uno");
        assertThat(result.numbers()).extracting(SpanishNumberNormalizer.NumberMention::value)
                .containsExactly(100, 6, 101, 1);
        assertThat(result.toString()).isEqualTo("Result[redacted]");
        assertThat(result.numbers().getFirst().toString()).isEqualTo("NumberMention[redacted]");
    }

    @Test
    void rejectsEntireOutOfRangeCompoundsWithoutLosingSeparateMentions() {
        for (String text : List.of("mil ciento treinta y dos", "doscientos mil treinta y seis",
                "mil treinta y dos", "mil ciento treinta y", "mil ciento treinta y diez")) {
            assertThat(SpanishNumberNormalizer.normalize(text).numbers()).as(text).isEmpty();
        }
        var result = SpanishNumberNormalizer.normalize("mil ciento treinta y dos, seis y siete");
        assertThat(result.numbers()).extracting(SpanishNumberNormalizer.NumberMention::value)
                .containsExactly(6, 7);
    }

    @Test
    void rejectsUnsupportedAndPartialExpressions() {
        for (String text : List.of("", "sin números", "seisabc abc6 6abc _6", "121", "999999999999999999999",
                "-6", "+6", "6,5", "3.14", "treinta-y-tres", "ciento veintiuno", "ciento veinte y seis", "ciento treinta y tres", "doscientos seis",
                "dos mil seis", "ciento", "treinta y", "treinta y diez", "veinte y seis")) {
            assertThat(SpanishNumberNormalizer.normalize(text).numbers()).as(text).isEmpty();
        }
        assertThat(SpanishNumberNormalizer.normalize("seis, siete y seis").numbers())
                .extracting(SpanishNumberNormalizer.NumberMention::value).containsExactly(6, 7, 6);
        assertThatNullPointerException().isThrownBy(() -> SpanishNumberNormalizer.normalize(null));
    }
}
