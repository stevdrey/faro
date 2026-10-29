// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.assessment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import io.github.stevdrey.faro.assessment.SpanishNumberNormalizer.NumberMention;
import io.github.stevdrey.faro.assessment.SpanishTokens.Token;

/** Bounded, literal extraction from Spanish ASR hypotheses; any unsupported part voids the whole input. */
public final class SpanishExplanationInterpreter implements ExplanationInterpreter {
    private static final int MAX_LENGTH = 16_000;
    // The only non-space characters allowed between words; quotes, question marks, signs and symbols void the input.
    private static final String SEPARATORS = ".,;:!¡";
    private static final Set<String> NOUNS = Set.of("bloque", "bloques", "unidad", "unidades", "ficha", "fichas",
            "objeto", "objetos", "bola", "bolas", "manzana", "manzanas", "punto", "puntos");
    private static final Set<String> SEQUENCE = Set.of("luego", "despues", "entonces");
    private static final Map<String, Action> VERBS = Map.ofEntries(
            Map.entry("tenia", Action.HAD),
            Map.entry("agregue", Action.ADDED), Map.entry("sume", Action.ADDED), Map.entry("puse", Action.ADDED),
            Map.entry("anadi", Action.ADDED), Map.entry("quite", Action.REMOVED), Map.entry("reste", Action.REMOVED),
            Map.entry("saque", Action.REMOVED), Map.entry("retire", Action.REMOVED),
            Map.entry("quedo", Action.REMAINED), Map.entry("quedaron", Action.REMAINED));

    @Override
    public List<Evidence> interpret(String transcript) {
        Objects.requireNonNull(transcript, "transcript");
        if (transcript.isBlank() || transcript.length() > MAX_LENGTH) return List.of();
        var tokens = SpanishTokens.tokenize(transcript);
        if (!onlySpacesAndSeparators(transcript, tokens)) return List.of();
        var normalized = SpanishNumberNormalizer.normalize(transcript, tokens);
        if (!normalized.cues().isEmpty()) return List.of();
        var numbers = new HashMap<Integer, NumberMention>();
        for (var mention : normalized.numbers()) numbers.put(mention.start(), mention);

        var evidence = new ArrayList<Evidence>();
        int start = 0;
        for (int i = 1; i <= tokens.size(); i++) {
            if (i == tokens.size() || isBoundary(transcript, tokens, i)) {
                // Reported speech, lists, corrections, hedges and conditionals all surface as unsupported clauses.
                if (!parseClause(tokens, start, i, numbers, evidence)) return List.of();
                start = i;
            }
        }
        return List.copyOf(evidence);
    }

    @Override
    public OptionalInt standaloneNumber(String transcript) {
        Objects.requireNonNull(transcript, "transcript");
        if (transcript.isBlank() || transcript.length() > MAX_LENGTH) return OptionalInt.empty();
        var tokens = SpanishTokens.tokenize(transcript);
        if (tokens.isEmpty() || !onlySpacesAndSeparators(transcript, tokens)) return OptionalInt.empty();
        var normalized = SpanishNumberNormalizer.normalize(transcript, tokens);
        if (!normalized.cues().isEmpty() || normalized.numbers().size() != 1) return OptionalInt.empty();
        var number = normalized.numbers().getFirst();
        // Every word must belong to the number: "siete bloques" or "dijo siete" is not a bare answer.
        return tokens.getFirst().start() >= number.start() && tokens.getLast().end() <= number.end()
                ? OptionalInt.of(number.value()) : OptionalInt.empty();
    }

    private static boolean onlySpacesAndSeparators(String text, List<Token> tokens) {
        int from = 0;
        for (var token : tokens) {
            if (!onlySpacesAndSeparators(text, from, token.start())) return false;
            from = token.end();
        }
        return onlySpacesAndSeparators(text, from, text.length());
    }

    private static boolean onlySpacesAndSeparators(String text, int from, int to) {
        for (int i = from; i < to; i++) {
            char c = text.charAt(i);
            if (!SpanishTokens.isSpace(c) && SEPARATORS.indexOf(c) < 0) return false;
        }
        return true;
    }

    private static boolean isBoundary(String text, List<Token> tokens, int i) {
        for (int c = tokens.get(i - 1).end(); c < tokens.get(i).start(); c++) {
            if (SEPARATORS.indexOf(text.charAt(c)) >= 0) return true;
        }
        // Split before "y" only when it introduces a recognized clause, not a compound number.
        return tokens.get(i).word().equals("y") && startsClause(tokens, i + 1);
    }

    private static boolean startsClause(List<Token> tokens, int i) {
        if (i < tokens.size() && SEQUENCE.contains(tokens.get(i).word())) i++;
        if (i < tokens.size() && (tokens.get(i).word().equals("yo") || tokens.get(i).word().equals("me"))) i++;
        if (i >= tokens.size()) return false;
        return VERBS.containsKey(tokens.get(i).word()) || tokens.get(i).word().equals("empece");
    }

    /** Returns false for an unsupported clause; a clause of joining words alone is accepted without evidence. */
    private static boolean parseClause(List<Token> tokens, int from, int to, Map<Integer, NumberMention> numbers,
                                       List<Evidence> output) {
        int i = from;
        if (tokens.get(i).word().equals("y")) i++;
        if (i < to && SEQUENCE.contains(tokens.get(i).word())) i++;
        if (i == to) return true;
        int phraseStart = tokens.get(i).start();
        if (tokens.get(i).word().equals("yo")) i++;
        boolean me = i < to && tokens.get(i).word().equals("me");
        if (me) i++;
        if (i >= to) return false;
        String verb = tokens.get(i++).word();
        Action action = VERBS.get(verb);
        if (verb.equals("empece")) {
            action = Action.HAD;
            if (i >= to || !tokens.get(i++).word().equals("con")) return false;
        }
        if (action == null || (me && action != Action.REMAINED) || i >= to) return false;
        int phraseEnd = tokens.get(to - 1).end();
        var number = numbers.get(tokens.get(i).start());
        if (number == null || number.end() > phraseEnd) return false;
        while (i < to && tokens.get(i).end() <= number.end()) i++;
        if (i < to && NOUNS.contains(tokens.get(i).word())) i++;
        if (i != to) return false;
        output.add(new Evidence(action, number.value(), phraseStart, phraseEnd, number.start(), number.end()));
        return true;
    }
}
