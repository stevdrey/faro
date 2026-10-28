// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.assessment;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Bounded, literal extraction from Spanish ASR hypotheses. */
public final class SpanishExplanationInterpreter implements ExplanationInterpreter {
    private static final int MAX_LENGTH = 16_000;
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{M}\\p{N}_]+");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
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
        if (transcript.isBlank() || transcript.length() > MAX_LENGTH
                || transcript.indexOf('¿') >= 0 || transcript.indexOf('?') >= 0) return List.of();
        var normalized = SpanishNumberNormalizer.normalize(transcript);
        if (!normalized.cues().isEmpty()) return List.of();

        var tokens = new ArrayList<Token>();
        var matcher = TOKEN.matcher(transcript);
        while (matcher.find()) tokens.add(new Token(fold(matcher.group()), matcher.start(), matcher.end()));
        var evidence = new ArrayList<Evidence>();
        int start = 0;
        for (int i = 0; i <= tokens.size(); i++) {
            if (i == tokens.size() || (i > start && isBoundary(transcript, tokens, i))) {
                parseClause(transcript, tokens, start, i, normalized.numbers(), evidence);
                start = i;
            }
        }
        return List.copyOf(evidence);
    }

    private static boolean isBoundary(String text, List<Token> tokens, int i) {
        var before = tokens.get(i - 1);
        var current = tokens.get(i);
        String gap = text.substring(before.end(), current.start());
        if (gap.codePoints().anyMatch(cp -> cp == '.' || cp == ',' || cp == ';' || cp == ':'
                || cp == '!' || cp == '?' || cp == '¡' || cp == '¿')) return true;
        // Split before "y" only when it introduces a recognized clause, not a compound number.
        return current.word().equals("y") && i + 1 < tokens.size()
                && whitespace(gap) && startsClause(tokens, i + 1);
    }

    private static boolean startsClause(List<Token> tokens, int i) {
        if (i < tokens.size() && SEQUENCE.contains(tokens.get(i).word())) i++;
        if (i < tokens.size() && (tokens.get(i).word().equals("yo") || tokens.get(i).word().equals("me"))) i++;
        if (i >= tokens.size()) return false;
        return VERBS.containsKey(tokens.get(i).word()) || tokens.get(i).word().equals("empece");
    }

    private static void parseClause(String text, List<Token> tokens, int from, int to,
                                    List<SpanishNumberNormalizer.NumberMention> numbers, List<Evidence> output) {
        if (from == to) return;
        int first = tokens.get(from).start(), last = tokens.get(to - 1).end();
        int before = first - 1, after = last;
        while (before >= 0 && space(text.charAt(before))) before--;
        while (after < text.length() && space(text.charAt(after))) after++;
        if ((before >= 0 && "¿?+-−‐‑–—/".indexOf(text.charAt(before)) >= 0)
                || (after < text.length() && "¿?+-−‐‑–—/".indexOf(text.charAt(after)) >= 0)) return;
        // Do not accept a fragment inside quotes or a malformed mixed decimal like "seis.5".
        if ((first > 0 && !space(text.charAt(first - 1)) && ".,;:!¡".indexOf(text.charAt(first - 1)) < 0)
                || (last < text.length() && !space(text.charAt(last)) && ".,;:!¡".indexOf(text.charAt(last)) < 0)
                || (last + 1 < text.length() && ".,".indexOf(text.charAt(last)) >= 0
                    && Character.isDigit(text.charAt(last + 1)))) return;
        // Only whitespace is allowed between words; stray punctuation cannot create evidence.
        for (int i = from + 1; i < to; i++) {
            if (!whitespace(text.substring(tokens.get(i - 1).end(), tokens.get(i).start()))) return;
        }
        int i = from;
        if (tokens.get(i).word().equals("y")) i++;
        if (i < to && SEQUENCE.contains(tokens.get(i).word())) i++;
        int phraseStart = i < to ? tokens.get(i).start() : 0;
        if (i < to && tokens.get(i).word().equals("yo")) i++;
        boolean me = i < to && tokens.get(i).word().equals("me");
        if (me) i++;
        if (i >= to) return;
        String verb = tokens.get(i++).word();
        Action action = VERBS.get(verb);
        if (verb.equals("empece")) {
            action = Action.HAD;
            if (i >= to || !tokens.get(i++).word().equals("con")) return;
        }
        if (action == null || (me && action != Action.REMAINED) || i >= to) return;
        int numberStart = tokens.get(i).start();
        SpanishNumberNormalizer.NumberMention number = null;
        for (var candidate : numbers) {
            if (candidate.start() == numberStart) { number = candidate; break; }
        }
        if (number == null) return;
        while (i < to && tokens.get(i).end() <= number.end()) i++;
        if (i < to && NOUNS.contains(tokens.get(i).word())) i++;
        if (i != to) return;
        output.add(new Evidence(action, number.value(), phraseStart, tokens.get(to - 1).end(),
                number.start(), number.end()));
    }

    private static boolean whitespace(String value) {
        return !value.isEmpty() && value.codePoints().allMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp));
    }

    private static boolean space(char value) { return Character.isWhitespace(value) || Character.isSpaceChar(value); }

    private static String fold(String value) {
        return MARKS.matcher(Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)).replaceAll("");
    }

    private record Token(String word, int start, int end) {}
}
