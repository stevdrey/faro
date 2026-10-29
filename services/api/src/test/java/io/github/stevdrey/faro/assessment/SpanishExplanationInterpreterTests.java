// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.assessment;

import static io.github.stevdrey.faro.assessment.ExplanationInterpreter.Action.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpanishExplanationInterpreterTests {
    private final ExplanationInterpreter interpreter = new SpanishExplanationInterpreter();

    @Test
    void extractsDocumentedNaturalAndSyntheticPatterns() {
        record Example(String text, ExplanationInterpreter.Action action, int quantity) {}
        var examples = List.of(
                new Example("tenía doce", HAD, 12),
                new Example("yo tenia 12 bloques", HAD, 12),
                new Example("empecé con veinte fichas", HAD, 20),
                new Example("agregué seis", ADDED, 6),
                new Example("sumé 6 unidades", ADDED, 6),
                new Example("puse una ficha", ADDED, 1),
                new Example("añadí treinta y dos puntos", ADDED, 32),
                new Example("quité ocho", REMOVED, 8),
                new Example("resté 28", REMOVED, 28),
                new Example("saqué dos bolas", REMOVED, 2),
                new Example("retiré un bloque", REMOVED, 1),
                new Example("quedaron veinticinco", REMAINED, 25),
                new Example("me quedaron 25 objetos", REMAINED, 25),
                new Example("quedó cero", REMAINED, 0),
                new Example("TENI\u0301A CIENTO VEINTE", HAD, 120));
        for (var example : examples) {
            var result = interpreter.interpret(example.text());
            assertThat(result).as(example.text()).hasSize(1);
            var item = result.getFirst();
            assertThat(item.action()).isEqualTo(example.action());
            assertThat(item.quantity()).isEqualTo(example.quantity());
            assertThat(example.text().substring(item.phraseStart(), item.phraseEnd())).isEqualTo(example.text());
            assertThat(SpanishNumberNormalizer.normalize(example.text()).numbers().getFirst().start()).isEqualTo(item.numberStart());
            assertThat(SpanishNumberNormalizer.normalize(example.text()).numbers().getFirst().end()).isEqualTo(item.numberEnd());
        }
    }

    @Test
    void preservesOrderRepetitionAndOriginalOffsetsWithoutJudgingArithmetic() {
        String text = ". Teni\u0301a doce bloques y luego sumé seis, quité seis; me quedaron 99.";
        var result = interpreter.interpret(text);
        assertThat(result).extracting(ExplanationInterpreter.Evidence::action).containsExactly(HAD, ADDED, REMOVED, REMAINED);
        assertThat(result).extracting(ExplanationInterpreter.Evidence::quantity).containsExactly(12, 6, 6, 99);
        assertThat(result.stream().map(e -> text.substring(e.phraseStart(), e.phraseEnd())).toList())
                .containsExactly("Teni\u0301a doce bloques", "sumé seis", "quité seis", "me quedaron 99");
        assertThat(result.stream().map(e -> text.substring(e.numberStart(), e.numberEnd())).toList())
                .containsExactly("doce", "seis", "seis", "99");
        assertThatThrownBy(() -> result.clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(result.toString()).doesNotContain("99", "12", "seis");
    }

    @Test
    void rejectsNegationAndUncertaintyAcrossTheWholeInput() {
        for (String text : List.of("tenía seis y no puse dos", "quizá tenía seis", "tenía seis; tal vez quedaron dos",
                "nunca quité dos", "tenía seis, probablemente")) {
            assertThat(interpreter.interpret(text)).as(text).isEmpty();
        }
    }

    @Test
    void rejectsConditionalAndAffirmativeSiClauses() {
        for (String text : List.of("si tenía seis y puse dos", "SI tenía seis, puse dos y quité uno",
                "tenía seis; Si puse dos", "tenía seis y si puse dos", "porque sí; puse seis",
                "porque SÍ; puse seis", "porque si\u0301; puse seis", "puse seis, sí")) {
            assertThat(interpreter.interpret(text)).as(text).isEmpty();
        }
        assertThat(interpreter.interpret("tenía seis y puse dos"))
                .extracting(ExplanationInterpreter.Evidence::action).containsExactly(HAD, ADDED);
    }

    @Test
    void acceptsClausesOfJoiningWordsAlone() {
        assertThat(interpreter.interpret("tenía seis, y luego, puse dos"))
                .extracting(ExplanationInterpreter.Evidence::action).containsExactly(HAD, ADDED);
        assertThat(interpreter.interpret("y tenía seis")).extracting(ExplanationInterpreter.Evidence::action)
                .containsExactly(HAD);
        assertThat(interpreter.interpret("¡puse dos!")).extracting(ExplanationInterpreter.Evidence::action)
                .containsExactly(ADDED);
    }

    @Test
    void rejectsQuotesAnywhereIncludingUnmatchedOrSpacedDelimiters() {
        for (String text : List.of("dijo: « tenía seis »", "dijo: «tenía seis»", "dijo: \" tenía seis \"",
                "dijo: ' tenía seis '", "dijo: ‘ tenía seis ’", "dijo: “ tenía seis ”",
                "dijo: ‹ tenía seis ›", "dijo: « tenía seis; puse dos », quité uno",
                "tenía seis; dijo: «puse dos", "puse dos »", "＂ tenía seis ＂", "＇ tenía seis ＇",
                "″ tenía seis ″", "` tenía seis `", "「tenía seis」", "tenía seis？", "tenía seis 😀")) {
            assertThat(interpreter.interpret(text)).as(text).isEmpty();
        }
    }

    @Test
    void rejectsWholeInputWhenAnyClauseIsUnsupported() {
        for (String text : List.of("porque", "tenía", "tenía 121", "tenía dos mil", "tenía seis o siete",
                "si tenía seis", "ella tenía seis", "¿tenía seis?", "¿ tenía seis ?", "tenía seis más dos",
                "tenía 6/2", "tenía seis-", "tenía seis juguetes", "me puse seis", "empecé seis",
                "tenía 6.5", "tenía seis.5", "puse treinta y", "tenía seis y siete", "‘tenía seis’",
                "¿tenía seis y puse dos y quité tres?", "Ana dijo: tenía seis", "Ana dijo, tenía seis",
                "ella, tenía seis", "mi hermano: puse dos", "tenía seis, siete", "tenía seis, siete u ocho",
                "quité dos, digo, tres", "tenía seis, más bien siete", "me quedaron seis, más o menos",
                "creo, tenía seis", "tenía 6 . 5", "tenía 1, 5", "tenía seis y luego", "tenía seis y y puse dos")) {
            assertThat(interpreter.interpret(text)).as(text).isEmpty();
        }
    }

    @Test
    void boundsInputAndRejectsNull() {
        assertThat(interpreter.interpret("")).isEmpty();
        assertThat(interpreter.interpret(" ".repeat(16_000))).isEmpty();
        assertThat(interpreter.interpret("tenía seis" + " ".repeat(16_000))).isEmpty();
        assertThat(interpreter.interpret("tenía seis" + " ".repeat(16_000 - "tenía seis".length()))).hasSize(1);
        assertThatNullPointerException().isThrownBy(() -> interpreter.interpret(null));
    }

    @Test
    void recognizesOnlyABareStandaloneNumber() {
        assertThat(interpreter.standaloneNumber("siete")).hasValue(7);
        assertThat(interpreter.standaloneNumber("Siete.")).hasValue(7);
        assertThat(interpreter.standaloneNumber(" treinta y dos!")).hasValue(32);
        assertThat(interpreter.standaloneNumber("7")).hasValue(7);
        for (String text : List.of("", "...", "siete bloques", "no siete", "siete u ocho", "¿siete?",
                "puse siete", "siete siete")) {
            assertThat(interpreter.standaloneNumber(text)).as(text).isEmpty();
        }
        assertThatNullPointerException().isThrownBy(() -> interpreter.standaloneNumber(null));
    }

    @Test
    void evidenceRejectsInvalidRanges() {
        assertThatNullPointerException().isThrownBy(() -> new ExplanationInterpreter.Evidence(null, 1, 0, 2, 0, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new ExplanationInterpreter.Evidence(HAD, -1, 0, 2, 0, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new ExplanationInterpreter.Evidence(HAD, 1, -1, 2, 0, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new ExplanationInterpreter.Evidence(HAD, 1, 1, 2, 0, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new ExplanationInterpreter.Evidence(HAD, 1, 0, 2, 1, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new ExplanationInterpreter.Evidence(HAD, 1, 0, 2, 0, 3))
                .withMessageNotContaining("3");
        assertThat(new ExplanationInterpreter.Evidence(HAD, 0, 0, 1, 0, 1).quantity()).isZero();
    }
}
