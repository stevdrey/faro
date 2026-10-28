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

/** Extracts literal number mentions and cue words, never an answer or an assessment. */
public final class SpanishNumberNormalizer {
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{M}\\p{N}_]+");
    private static final Pattern ASCII_DIGITS = Pattern.compile("[0-9]+");
    private static final Map<String, Integer> SMALL = Map.ofEntries(
            Map.entry("cero", 0), Map.entry("un", 1), Map.entry("uno", 1), Map.entry("una", 1),
            Map.entry("dos", 2), Map.entry("tres", 3), Map.entry("cuatro", 4), Map.entry("cinco", 5),
            Map.entry("seis", 6), Map.entry("siete", 7), Map.entry("ocho", 8), Map.entry("nueve", 9),
            Map.entry("diez", 10), Map.entry("once", 11), Map.entry("doce", 12), Map.entry("trece", 13),
            Map.entry("catorce", 14), Map.entry("quince", 15), Map.entry("dieciseis", 16),
            Map.entry("diecisiete", 17), Map.entry("dieciocho", 18), Map.entry("diecinueve", 19),
            Map.entry("veinte", 20), Map.entry("veintiun", 21), Map.entry("veintiuno", 21),
            Map.entry("veintiuna", 21), Map.entry("veintidos", 22), Map.entry("veintitres", 23),
            Map.entry("veinticuatro", 24), Map.entry("veinticinco", 25), Map.entry("veintiseis", 26),
            Map.entry("veintisiete", 27), Map.entry("veintiocho", 28), Map.entry("veintinueve", 29),
            Map.entry("treinta", 30), Map.entry("cuarenta", 40), Map.entry("cincuenta", 50),
            Map.entry("sesenta", 60), Map.entry("setenta", 70), Map.entry("ochenta", 80),
            Map.entry("noventa", 90), Map.entry("cien", 100));
    private static final Set<String> LARGE = Set.of("doscientos", "doscientas", "trescientos", "trescientas",
            "cuatrocientos", "cuatrocientas", "quinientos", "quinientas", "seiscientos", "seiscientas",
            "setecientos", "setecientas", "ochocientos", "ochocientas", "novecientos", "novecientas",
            "mil", "miles", "millon", "millones", "millardo", "millardos", "billon", "billones",
            "cientos", "centenar", "centenares", "docena", "docenas");
    private static final Set<String> DECIMAL_SEPARATORS = Set.of("coma", "punto");
    private static final String SIGNS = "+-−‐‑–—";
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Set<String> NEGATION = Set.of("no", "nunca", "jamas", "tampoco");
    private static final Set<String> UNCERTAINTY = Set.of("quiza", "quizas", "posiblemente", "probablemente");

    private SpanishNumberNormalizer() {}

    /** Offsets are zero-based UTF-16 indices into the original input; end is exclusive. */
    public record NumberMention(int value, int start, int end) {
        @Override
        public String toString() { return "NumberMention[redacted]"; }
    }
    public enum CueKind { NEGATION, UNCERTAINTY }
    public record Cue(CueKind kind, int start, int end) {}
    public record Result(List<NumberMention> numbers, List<Cue> cues) {
        public Result {
            numbers = List.copyOf(numbers);
            cues = List.copyOf(cues);
        }

        @Override
        public String toString() { return "Result[redacted]"; }
    }

    /** Matches only explicit forms. The caller must treat every result as unconfirmed. */
    public static Result normalize(String transcript) {
        Objects.requireNonNull(transcript, "transcript");
        var tokens = new ArrayList<Token>();
        var matcher = TOKEN.matcher(transcript);
        while (matcher.find()) {
            tokens.add(new Token(fold(matcher.group()), matcher.start(), matcher.end()));
        }
        var numbers = new ArrayList<NumberMention>();
        var cues = new ArrayList<Cue>();
        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (NEGATION.contains(token.word())) cues.add(new Cue(CueKind.NEGATION, token.start(), token.end()));
            if (UNCERTAINTY.contains(token.word())) cues.add(new Cue(CueKind.UNCERTAINTY, token.start(), token.end()));
            if (token.word().equals("tal") && nextIs(tokens, i, "vez", transcript)) {
                cues.add(new Cue(CueKind.UNCERTAINTY, token.start(), tokens.get(i + 1).end()));
            }
        }
        for (int i = 0; i < tokens.size();) {
            var token = tokens.get(i);
            if (!numericWord(token.word())) { i++; continue; }
            int end = numericExpressionEnd(tokens, i, transcript);
            boolean rejected = spokenSign(tokens, i, transcript);
            // Spoken decimals and digit groups ("seis coma cinco", "1 000") are one unsupported number.
            while (end < tokens.size()) {
                var previous = tokens.get(end - 1);
                var next = tokens.get(end);
                if (ASCII_DIGITS.matcher(previous.word()).matches() && next.word().length() == 3
                        && ASCII_DIGITS.matcher(next.word()).matches()
                        && separatedByWhitespace(previous, next, transcript)) {
                    end++;
                } else if (DECIMAL_SEPARATORS.contains(next.word()) && separatedByWhitespace(previous, next, transcript)
                        && end + 1 < tokens.size() && numericWord(tokens.get(end + 1).word())
                        && separatedByWhitespace(next, tokens.get(end + 1), transcript)) {
                    end = numericExpressionEnd(tokens, end + 1, transcript);
                } else {
                    break;
                }
                rejected = true;
            }
            var mention = rejected ? null : parse(tokens, i, end, transcript);
            if (mention != null) numbers.add(mention);
            i = end;
        }
        return new Result(numbers, cues);
    }

    /* Only group constructions which might be a compound; ordinary lists such as "seis y siete" stay separate. */
    private static int numericExpressionEnd(List<Token> tokens, int start, String text) {
        int end = start + 1;
        String first = tokens.get(start).word();
        if (first.equals("ciento") || LARGE.contains(first)) {
            if (first.equals("ciento") && end < tokens.size()
                    && separatedByWhitespace(tokens.get(start), tokens.get(end), text)) {
                Integer tail = SMALL.get(tokens.get(end).word());
                if (tail != null && tail >= 1 && tail <= 20) {
                    boolean followedByScale = nextIsLarge(tokens, end, text);
                    boolean followedByConjunction = isConjunctionTens(tokens.get(end).word())
                            && nextIs(tokens, end, "y", text);
                    if (!followedByScale && !followedByConjunction) return end + 1;
                }
            }
            boolean hasScale = LARGE.contains(first);
            while (end < tokens.size() && separatedByWhitespace(tokens.get(end - 1), tokens.get(end), text)) {
                String word = tokens.get(end).word();
                if (!numericWord(word) && !(hasScale && word.equals("y"))) break;
                hasScale |= LARGE.contains(word);
                end++;
            }
            // Consume the tens suffix even after multiple out-of-range numeric words.
            if (end > start + 1) end = tensConjunctionEnd(tokens, end - 1, text);
        } else {
            end = tensConjunctionEnd(tokens, start, text);
            if (end < tokens.size() && separatedByWhitespace(tokens.get(end - 1), tokens.get(end), text)
                    && LARGE.contains(tokens.get(end).word())) {
                end++;
                while (end < tokens.size() && separatedByWhitespace(tokens.get(end - 1), tokens.get(end), text)
                        && (numericWord(tokens.get(end).word()) || tokens.get(end).word().equals("y"))) end++;
            }
        }
        return end;
    }

    /*
     * A tens word plus "y" is a compound only when a number follows. Another tens word ("veinte y treinta")
     * and a non-number word ("treinta y tú") end the mention; a dangling "y" keeps it incomplete.
     */
    private static int tensConjunctionEnd(List<Token> tokens, int index, String text) {
        if (!isConjunctionTens(tokens.get(index).word()) || !nextIs(tokens, index, "y", text)) return index + 1;
        int after = index + 2;
        if (after >= tokens.size() || !separatedByWhitespace(tokens.get(index + 1), tokens.get(after), text)) {
            return after;
        }
        String word = tokens.get(after).word();
        return numericWord(word) && !isTens(word) ? after + 1 : index + 1;
    }

    /* "menos seis" is a negative number; "cinco menos dos" is a subtraction whose terms stay separate. */
    private static boolean spokenSign(List<Token> tokens, int index, String text) {
        if (index == 0 || !tokens.get(index - 1).word().equals("menos")
                || !separatedByWhitespace(tokens.get(index - 1), tokens.get(index), text)) return false;
        return index < 2 || !numericWord(tokens.get(index - 2).word())
                || !separatedByWhitespace(tokens.get(index - 2), tokens.get(index - 1), text);
    }

    private static NumberMention parse(List<Token> tokens, int start, int end, String text) {
        var first = tokens.get(start);
        var last = tokens.get(end - 1);
        // A hyphenated form is not part of the accepted grammar: don't expose its pieces.
        if ((first.start() > 0 && SIGNS.indexOf(text.charAt(first.start() - 1)) >= 0)
                || (last.end() + 1 < text.length() && SIGNS.indexOf(text.charAt(last.end())) >= 0
                && Character.isLetterOrDigit(text.codePointAt(last.end() + 1)))) return null;
        int count = end - start;
        Integer value = null;
        if (count == 1) {
            value = SMALL.get(first.word());
            if (ASCII_DIGITS.matcher(first.word()).matches() && first.word().length() <= 3
                    && !touchesNumericNotation(first, text)) {
                int digits = Integer.parseInt(first.word());
                if (digits <= 120) value = digits;
            }
        } else if (first.word().equals("ciento") && count == 2) {
            Integer tail = SMALL.get(tokens.get(start + 1).word());
            if (tail != null && tail >= 1 && tail <= 20) value = 100 + tail;
        } else if (count == 3 && tokens.get(start + 1).word().equals("y")) {
            Integer tens = SMALL.get(first.word());
            Integer unit = SMALL.get(tokens.get(start + 2).word());
            if (tens != null && tens >= 30 && tens <= 90 && tens % 10 == 0
                    && unit != null && unit >= 1 && unit <= 9) value = tens + unit;
        }
        return value == null ? null : new NumberMention(value, first.start(), last.end());
    }

    /* Signs are rejected by the caller; this covers decimals, times ("6:30") and fractions ("1/2"). */
    private static boolean touchesNumericNotation(Token token, String text) {
        int start = token.start(), end = token.end();
        return (start > 0 && ".,".indexOf(text.charAt(start - 1)) >= 0)
                || (start > 1 && ":/".indexOf(text.charAt(start - 1)) >= 0 && Character.isDigit(text.charAt(start - 2)))
                || (end + 1 < text.length() && ".,:/".indexOf(text.charAt(end)) >= 0
                && Character.isDigit(text.charAt(end + 1)));
    }

    private static boolean numericWord(String word) {
        return SMALL.containsKey(word) || word.equals("ciento") || LARGE.contains(word)
                || ASCII_DIGITS.matcher(word).matches();
    }

    private static boolean isTens(String word) {
        Integer value = SMALL.get(word);
        return value != null && value >= 20 && value <= 90 && value % 10 == 0;
    }

    /* "diez y seis" is a variant spelling of a compound, like "veinte y seis". */
    private static boolean isConjunctionTens(String word) {
        return isTens(word) || word.equals("diez");
    }

    private static boolean nextIsLarge(List<Token> tokens, int index, String text) {
        return index + 1 < tokens.size() && LARGE.contains(tokens.get(index + 1).word())
                && separatedByWhitespace(tokens.get(index), tokens.get(index + 1), text);
    }

    private static boolean nextIs(List<Token> tokens, int index, String word, String text) {
        return index + 1 < tokens.size() && tokens.get(index + 1).word().equals(word)
                && separatedByWhitespace(tokens.get(index), tokens.get(index + 1), text);
    }

    private static boolean separatedByWhitespace(Token a, Token b, String text) {
        return a.end() < b.start() && text.substring(a.end(), b.start()).codePoints()
                .allMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp));
    }

    private static String fold(String text) {
        return MARKS.matcher(Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)).replaceAll("");
    }

    private record Token(String word, int start, int end) {}
}
