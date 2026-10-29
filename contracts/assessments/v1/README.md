# Assessment result contract, version 1

FARO-004 / [issue #22](https://github.com/stevdrey/faro/issues/22) defines independent assessment states and minimal provenance. It introduces no grading rules, feedback policy, HTTP endpoint, persistence, UI, or speech/model integration.

## Payload

| Field | JSON type | Meaning |
|---|---|---|
| `schemaVersion` | integer | Exactly `1`, independent of exercise/action schemas and the decision version. |
| `actionMathState` | string | `CORRECTA`, `INCORRECTA`, or `SIN_TERMINAR`. |
| `strategyEvidenceState` | string | `COHERENTE`, `ERROR_ARITMETICO_IDENTIFICABLE`, `INSUFICIENTE`, `CONFLICTO_ASR`, or `NO_EVALUABLE`. |
| `nextAction` | string | `CONTINUAR`, `PISTA`, `PEDIR_ACLARACION`, or `REPETIR_AUDIO`. |
| `ruleIds` | array of strings | Ordered rule identifiers; may be empty. Every element must be non-null and nonblank. |
| `assessmentVersion` | string | Non-null, nonblank version of the assessment logic that produced the result. |

All fields are required and non-null. The enum identifiers retain their canonical Spanish spelling and case. Numbers and booleans are never coerced into enum values or strings. `schemaVersion` must be an integer token: `"1"`, `1.0`, booleans, arrays, and unsupported versions are rejected. Unknown fields are rejected.

`ruleIds` and `assessmentVersion` are opaque provenance strings, preserved without trimming, sorting, or normalization. Blank means empty or entirely Java `String.isBlank()` whitespace. The contract does not impose a semantic-version format, rule catalog, uniqueness constraint, or nonempty rule list. FARO-021 defines real aggregation rule identifiers in a separate domain trace; `AssessmentTrace.ruleIds()` maps a trace to values this field accepts, but this wire contract does not emit them automatically. `synthetic-v1` and `synthetic.*.v1` in the fixtures are examples, not registered production rules.

The axes are independent. All 3 × 5 × 4 = 60 combinations can be represented; constructing or decoding a result does not select or enforce a pedagogical policy. In particular, `CORRECTA` can coexist with `INSUFICIENTE`, `CONFLICTO_ASR`, or `NO_EVALUABLE`. Insufficient evidence never means the child does not understand, and a speech failure must not change an action's mathematical correctness. Future evaluators and feedback policies must choose states conservatively; these data types do not compute them.

There are no raw audio, transcripts, expected answers, personal identifiers, timestamps, evidence references, or freeform feedback fields. The separate FARO-021 domain trace has input categories rather than individual evidence references; integration remains future work.

## Shared synthetic fixtures

| Fixture | Mathematical action | Strategy evidence | Next action |
|---|---|---|---|
| [coherent.json](fixtures/coherent.json) | `CORRECTA` | `COHERENTE` | `CONTINUAR` |
| [arithmetic-error.json](fixtures/arithmetic-error.json) | `INCORRECTA` | `ERROR_ARITMETICO_IDENTIFICABLE` | `PISTA` |
| [insufficient.json](fixtures/insufficient.json) | `CORRECTA` | `INSUFICIENTE` | `PEDIR_ACLARACION` |
| [asr-conflict.json](fixtures/asr-conflict.json) | `CORRECTA` | `CONFLICTO_ASR` | `PEDIR_ACLARACION` |
| [not-evaluable.json](fixtures/not-evaluable.json) | `CORRECTA` | `NO_EVALUABLE` | `REPETIR_AUDIO` |
| [incomplete.json](fixtures/incomplete.json) | `SIN_TERMINAR` | `NO_EVALUABLE` | `CONTINUAR` |

These illustrate result representation, not a policy table. The insufficient/conflict examples can describe six correct blocks with a vague explanation or an ASR hypothesis of seven. The not-evaluable example can describe correct actions with unavailable speech. The incomplete example has an empty rule list and demonstrates that absence of provenance is distinct from a missing field. None of these fixtures contains a child's evidence or drives an evaluator.

## Boundaries and versioning

The Java `assessment` domain contains immutable `AssessmentResult` and three enums, importing only JDK classes. `assessment.api.AssessmentResultPayload` adds `schemaVersion` and explicitly converts to/from the domain. Both constructors validate values and defensively copy the rule list; neither couples the state axes.

Incoming JSON must use `AssessmentResultPayloadJson`. Its private mapper, built by the shared `common.StrictContractJson` used by every versioned contract codec, rejects unknown fields, missing/null fields, invalid enums, scalar coercions, singleton array conversions, duplicate keys, trailing JSON values, and nonstandard syntax (comments, single quotes, unquoted names, leading zeros or plus signs, non-numeric numbers, missing values, and trailing commas). A root JSON `null` is rejected with a Jackson decoding error, like other malformed input. Enum serialization uses canonical names rather than ordinals. Writing always uses the record component names and includes every field, so an empty `ruleIds` list is never omitted. Codec strictness is pinned even when relevant application mapper settings are permissive; the application mapper itself is unchanged. A future HTTP endpoint must explicitly use this codec or an equivalent converter and add HTTP binding tests; plain `@RequestBody` binding does not guarantee the same strictness.

The web exports readonly types from `apps/web/src/contracts/assessment.ts`. TypeScript checks field shapes and literal states, but does not validate untrusted JSON, nonblank strings, or policy choices at runtime. Shared fixture type checks verify keys and primitive shapes; backend tests verify actual literal values, constructors, and serialization.

Wire shape or enum changes require a new schema version and explicit migration. Changes to assessment logic use `assessmentVersion` independently of the wire schema. There are no released assessment consumers or migrations yet. No new dependencies are introduced.

## Verification

```bash
gradle -p services/api --no-daemon test
npm --prefix apps/web run build
node .agents/scripts/pr-size-preflight.mjs
```

Use JDK 27, Gradle 9.8.1, Node 24, and npm 12.2.0 as required by the repository. Java tests cover all 60 combinations, shared fixtures, immutable provenance, conversions, invalid JSON, and permissive application mapper settings. The existing web build includes the contract and fixture type checks.
