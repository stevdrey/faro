"""Bounded benchmark scoring, deliberately separate from Faro assessment."""

import math
import re
import unicodedata


UNITS = dict(zip(
    "cero uno dos tres cuatro cinco seis siete ocho nueve diez once doce trece catorce "
    "quince dieciseis diecisiete dieciocho diecinueve veinte veintiuno veintidos "
    "veintitres veinticuatro veinticinco veintiseis veintisiete veintiocho veintinueve".split(),
    range(30)))
UNITS.update(un=1, una=1, veintiun=21)
TENS = dict(zip("treinta cuarenta cincuenta sesenta setenta ochenta noventa".split(),
                range(30, 100, 10)))
NONVERBAL_LABELS = frozenset({
    "musica", "music", "aplausos", "applause", "risas", "laughter",
    "ruido", "noise", "silencio", "silence", "blank_audio",
})


def folded(text):
    return "".join(c for c in unicodedata.normalize("NFD", text.lower())
                   if not unicodedata.combining(c))


def words(text):
    """Case/punctuation normalization only; preserve accents and numeric spelling."""
    return re.findall(r"\d+|[^\W\d_]+|[+−=\-]", unicodedata.normalize("NFC", text).lower())


def numbers(text):
    """Ordered nonnegative integers: Spanish 0–99 and digit tokens (no decimals)."""
    tokens = words(folded(text))
    result = []
    i = 0
    while i < len(tokens):
        token = tokens[i]
        if token.isdecimal():
            result.append(int(token))
        elif token in UNITS:
            result.append(UNITS[token])
        elif token in TENS:
            value = TENS[token]
            if (i + 2 < len(tokens) and tokens[i + 1] == "y"
                    and tokens[i + 2] in UNITS and 1 <= UNITS[tokens[i + 2]] <= 9):
                value += UNITS[tokens[i + 2]]
                i += 2
            result.append(value)
        i += 1
    return result


def transcript_diagnostics(text):
    """Conservative annotations only: never rewrite text, numbers or strict scores."""
    labels = []

    def label(match):
        if folded(match.group()[1:-1].strip()) in NONVERBAL_LABELS:
            labels.append(match.group())
            return " " * len(match.group())
        return match.group()  # Unknown tags may contain speech; never silently discard them.

    remaining = re.sub(r"(?<!\[)\[[^\[\]\r\n]{1,64}\](?!\])", label, text)
    if not text.strip():
        kind = "empty"
    elif labels:
        kind = "mixed" if words(remaining) else "nonverbal_only"
    else:
        kind = "speech_or_unclassified"

    tokens = list(re.finditer(r"\d+|[^\W\d_]+", remaining))
    ambiguous = []

    def integer(token):
        if token.isdecimal():
            return int(token) if len(token) <= 3 else None
        return UNITS.get(token, TENS.get(token))

    for left, conjunction, right in zip(tokens, tokens[1:], tokens[2:]):
        a, join, b = (folded(token.group()) for token in (left, conjunction, right))
        first, second = integer(a), integer(b)
        if (join != "y" or first is None or second is None
                or not (10 <= first <= 90 and first % 10 == 0 and 1 <= second <= 9)
                or not remaining[left.end():conjunction.start()].isspace()
                or not remaining[conjunction.end():right.start()].isspace()):
            continue
        if a in TENS and b in UNITS:
            continue  # Canonical spelled compounds already supported, e.g. treinta y seis.
        # Do not mistake part of a digit decimal for an integer variant.
        if (left.start() >= 2 and remaining[left.start() - 1] in ".,"
                and remaining[left.start() - 2].isdecimal()):
            continue
        if (right.end() + 1 < len(remaining) and remaining[right.end()] in ".,"
                and remaining[right.end() + 1].isdecimal()):
            continue
        ambiguous.append({"text": text[left.start():right.end()], "components": [first, second]})
    return {"version": 1, "content_kind": kind, "nonverbal_labels": labels,
            "numeric_ambiguities": ambiguous}


def edits(reference, hypothesis):
    """Levenshtein distance; insertions count, so WER can exceed 1."""
    previous = list(range(len(hypothesis) + 1))
    for row, expected in enumerate(reference, 1):
        current = [row]
        for col, actual in enumerate(hypothesis, 1):
            current.append(min(previous[col] + 1, current[-1] + 1,
                               previous[col - 1] + (expected != actual)))
        previous = current
    return previous[-1]


def percentile(values, percent):
    """Nearest rank, including p50 (not an interpolated median)."""
    if not values:
        return None
    ordered = sorted(values)
    return round(ordered[max(0, math.ceil(len(ordered) * percent / 100) - 1)], 3)


class Scores:
    def __init__(self):
        self.speech_clips = self.correct = self.failures = 0
        self.word_edits = self.reference_words = self.wer_clips = 0
        self.silence_clips = self.silence_nonempty = self.silence_failures = 0
        self.silence_empty = self.silence_nonverbal_only = self.silence_speech_or_unclassified = 0
        self.speech_numeric_ambiguities = 0

    def add(self, clip, text, *, failed=False):
        if clip["kind"] == "silence":
            self.silence_clips += 1
            self.silence_failures += failed
            self.silence_nonempty += bool(text.strip()) and not failed
            if not failed:
                kind = transcript_diagnostics(text)["content_kind"]
                self.silence_empty += kind == "empty"
                self.silence_nonverbal_only += kind == "nonverbal_only"
                self.silence_speech_or_unclassified += kind in ("mixed", "speech_or_unclassified")
            return
        self.speech_clips += 1
        self.failures += failed
        self.correct += not failed and numbers(text) == clip["critical_numbers"]
        if not failed:
            self.speech_numeric_ambiguities += bool(transcript_diagnostics(text)["numeric_ambiguities"])
            reference = words(clip["text"])
            self.reference_words += len(reference)
            self.word_edits += edits(reference, words(text))
            self.wer_clips += 1

    def summary(self):
        return {
            "speech_clips": self.speech_clips,
            "all_critical_numbers_correct": self.correct,
            "critical_number_accuracy": self.correct / self.speech_clips if self.speech_clips else None,
            "speech_technical_failures": self.failures,
            "speech_numeric_ambiguities": self.speech_numeric_ambiguities,
            "wer_clips": self.wer_clips,
            "word_edits": self.word_edits,
            "reference_words": self.reference_words,
            "wer": self.word_edits / self.reference_words if self.reference_words else None,
            "silence_clips": self.silence_clips,
            "silence_nonempty": self.silence_nonempty,
            "silence_technical_failures": self.silence_failures,
            "silence_empty": self.silence_empty,
            "silence_nonverbal_only": self.silence_nonverbal_only,
            "silence_speech_or_unclassified": self.silence_speech_or_unclassified,
        }
