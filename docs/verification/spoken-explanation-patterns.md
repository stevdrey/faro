# Bounded spoken-explanation patterns (FARO-019)

`ExplanationInterpreter` is a Java domain port; `SpanishExplanationInterpreter`
implements local deterministic extraction and is not connected to the ASR preview,
an endpoint, a user interface, or assessment. The input remains an unconfirmed
transcript hypothesis. Each immutable result contains an action (`HAD`, `ADDED`,
`REMOVED`, `REMAINED`), the stated quantity and zero-based, end-exclusive UTF-16
ranges for the phrase and its number in the original input. The phrase excludes
joining words such as `y`, `luego`, `después` and `entonces`; it includes an
optional `yo` or `me` and the recognized noun. Repeated statements are retained
in order. Empty evidence means insufficient evidence, never a mathematical failure.
No result, including a self-contradictory one, is checked or graded.

## Grammar

Each recognized clause consists of an optional `yo`, then one of:

| Action | Exact verbs or phrase (accent and case insensitive) |
| --- | --- |
| HAD | `tenía`, `empecé con` |
| ADDED | `agregué`, `sumé`, `puse`, `añadí` |
| REMOVED | `quité`, `resté`, `saqué`, `retiré` |
| REMAINED | `quedó`, `quedaron`, optionally preceded by `me` |

The verb or `con` must be followed by a number recognized by
[FARO-018](spanish-number-normalization.md), optionally followed by one of
`bloque(s)`, `unidad(es)`, `ficha(s)`, `objeto(s)`, `bola(s)`, `manzana(s)` or
`punto(s)`. Parentheses here denote documented singular and plural words, not
literal input. Only whitespace may separate the words in a clause. Clauses may
be separated by `.`, `,`, `;`, `:`, `!`, `¡`, or by `y` immediately followed
by another recognized clause, optionally with `luego`, `después` or `entonces`.
Those sequence words may also introduce a clause after punctuation, and a clause
made only of `y` and/or a sequence word is accepted without evidence. Compound
numbers such as `treinta y dos` remain a single number.

For example, `tenía doce bloques y luego sumé seis; me quedaron 18` produces
three literal observations. Any clause outside this grammar yields no evidence
for the entire input: extra words, implied quantities, other subjects or
reported speech (`Ana dijo: tenía seis`, `ella, tenía seis`), lists and
self-corrections (`tenía seis, siete`, `quité dos, digo, tres`), hedges
(`me quedaron seis, más o menos`), fillers (`porque sí; puse seis`) and
unsupported number forms (`tenía 6 . 5`, `tenía seis.5`). The finite grammar
cannot understand all Spanish phrasing or all ambiguity.

Outside words, only whitespace (including Unicode space separators) and the
separators above may appear. Any other character anywhere, such as a question
mark, any quote delimiter (`"`, `«`, `＂`, `″`, `` ` ``, `「`), a sign, a slash or
an emoji, yields no evidence for the entire input, even when a quote is
unmatched. So does a negation or uncertainty cue detected anywhere by FARO-018.
Conditional `si` and affirmative `sí` cannot belong to a recognized clause, so
either one voids the input. The cue vocabulary is finite
and is not a complete detector of negation or uncertainty. Blank input and input
over 16,000 UTF-16 code units also yield no evidence, without truncation; null
is rejected. This is a conservative extraction policy, not a determination of
speech quality, understanding or ASR reliability.

The interpreter neither stores nor logs the input. Evidence string representations
are redacted, but values and offsets remain accessible to callers and must be
treated as sensitive. No raw recordings or personal pilot evidence are used.

## Verification

With JDK 27 and the project's Gradle dependencies available:

```bash
gradle -p services/api --no-daemon test --tests '*SpanishExplanationInterpreterTests'
gradle -p services/api --no-daemon test
```

The synthetic tests cover at least 12 formulations, all four actions, ordered
and repeated claims, original offsets, case and decomposed accents, immutable
and validated results, whole-input rejection of unsupported clauses and
characters, and input limits. They do not validate child
speech, ASR accuracy or readiness for a pilot.
