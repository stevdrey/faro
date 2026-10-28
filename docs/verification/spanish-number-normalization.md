# Spanish number normalization (FARO-018)

`SpanishNumberNormalizer.normalize` is a pure assessment-domain function. It
accepts a transcript string and returns immutable, ordered number mentions and
separate literal cues. Each mention contains a value from 0 to 120 and a
zero-based, end-exclusive UTF-16 range into the exact original string. Repeated
mentions remain repeated. Cues carry their range and either `NEGATION` or
`UNCERTAINTY`; they are not associated with numbers and do not establish scope,
truth, confidence or an answer. ASR transcripts and all extracted values remain
unconfirmed hypotheses.

Recognized forms are ASCII integer digits and standard Spanish cardinal words
from `cero` through `ciento veinte`, including `un`, `uno`, `una`, `veintiún`,
`veintiuno`, `veintiuna`, and tens joined with `y` and a unit. Matching ignores
case and accent marks, including decomposed accents. Digits with more than three
characters, signs, decimal notation and numeric expressions outside the range
are not interpreted. Unsupported or incomplete recognized compounds are
rejected as a whole. The vocabulary is deliberately finite: it cannot identify
all ASR errors, ambiguous grammar, implicit quantities, or every possible
out-of-range expression. Adjacent ordinary mentions are not combined into an
invented number.
Completed supported hundreds can be followed by another number without punctuation;
an immediately following scale word or tens conjunction is treated as part of
the potentially out-of-range compound.
An ASCII digit immediately following a period or comma is conservatively rejected,
including decimals without an integer part; use whitespace to separate list items.

Cue vocabulary (case and accent insensitive): `no`, `nunca`, `jamás`, `tampoco`;
`quizá`, `quizás`, `tal vez`, `posiblemente`, `probablemente`. These are literal
signals only, not complete negation or uncertainty detection. Whitespace is
required within multiword forms. The function neither retains nor logs input;
callers must protect the transient transcript and avoid logging results as
personal data. Result and number-mention string representations are redacted.
It adds no endpoint, UI, provider coupling, assessment decision,
persistence or dependencies.

Tests exercise every integer in both forms, offsets and repeated mentions,
variants, explicit cues, empty inputs, malformed compounds and bounds. Run
`gradle -p services/api --no-daemon test` with JDK 27.
