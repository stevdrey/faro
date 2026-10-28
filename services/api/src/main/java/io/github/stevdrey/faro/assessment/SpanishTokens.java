// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.assessment;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Shared word tokenization so number mentions and explanation clauses agree on offsets. */
final class SpanishTokens {
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{M}\\p{N}_]+");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    private SpanishTokens() {}

    /** Folded word plus its zero-based, end-exclusive UTF-16 range in the original text. */
    record Token(String word, int start, int end) {}

    static List<Token> tokenize(String text) {
        var tokens = new ArrayList<Token>();
        var matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            tokens.add(new Token(fold(matcher.group()), matcher.start(), matcher.end()));
        }
        return tokens;
    }

    static String fold(String text) {
        return MARKS.matcher(Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)).replaceAll("");
    }

    /* Non-breaking and other Unicode space separators count as whitespace. */
    static boolean isSpace(int cp) {
        return Character.isWhitespace(cp) || Character.isSpaceChar(cp);
    }
}
