# Exercise contract, version 1

FARO-002 / [issue #10](https://github.com/stevdrey/faro/issues/10) defines the public exercise presentation shared by the API and web client. This contract does not introduce an HTTP endpoint or solve exercises.

| Field | JSON type | Meaning |
|---|---|---|
| `exerciseId` | string | Nonblank opaque identifier; no personal information. |
| `schemaVersion` | integer | Exactly `1`. |
| `type` | string | `MISSING_ADDEND`, `SEQUENCE`, or `SUBTRACTION`. |
| `operands` | object | Known numeric inputs, with the shape determined by `type`. |
| `visualModel` | string | `BLOCKS`, `NUMBER_LINE`, or `TENS_AND_ONES`. |
| `difficulty` | string | `EASY`, `MEDIUM`, or `HARD`; descriptive metadata only. |

All six fields are required and non-null. Unknown fields and enum values are rejected. JSON integers use the Java signed 32-bit range (`-2147483648` through `2147483647`); strings, booleans, and fractional numeric tokens are not coerced into integers. Identifiers are preserved as supplied after the nonblank check. No mathematical range or difficulty-assignment rules are defined here.

## Families

| Type | Operand shape and interpretation | Initial visual model | Fixture |
|---|---|---|---|
| `MISSING_ADDEND` | `{ "total": 18, "knownAddend": 12 }` presents `18 = 12 + □`. The missing addend is always the second addend. | `BLOCKS` | [missing-addend.json](fixtures/missing-addend.json) |
| `SEQUENCE` | `{ "terms": [30, 28] }` presents `30, 28, □`. At least two ordered, visible integer terms are required; the next term is omitted. | `NUMBER_LINE` | [sequence.json](fixtures/sequence.json) |
| `SUBTRACTION` | `{ "minuend": 53, "subtrahend": 28 }` presents `53 − 28`. | `TENS_AND_ONES` | [subtraction.json](fixtures/subtraction.json) |

The initial visual models above are presentation choices, not enforced family-to-model restrictions. Every operand field is required and non-null; sequence terms cannot contain nulls. The fixtures are synthetic public payloads and contain no solutions or pilot data.

## Backend boundary and client types

`ExerciseDefinition` is a backend-only immutable domain record with an `expectedResult` integer. Its family is derived from its typed operands, so family/operand mismatches cannot be constructed. The domain imports neither Jackson nor Spring nor a model-provider SDK.

`ExercisePayload.from(definition)` explicitly projects a definition to a sealed public DTO variant, attaches `schemaVersion: 1`, and omits the solution. Future controllers must return this DTO, never serialize `ExerciseDefinition` directly. Jackson adds the top-level `type` discriminator; no implementation class names or nested operand discriminators appear in JSON. Application-wide Jackson settings reject missing creator properties, null primitives, unknown fields, and scalar coercion. `ExerciseJsonConfiguration` additionally rejects numbers and booleans where a JSON string is required, which Jackson's scalar-coercion flag alone does not cover.

The web client exports the matching readonly discriminated union from `apps/web/src/contracts/exercise.ts`. TypeScript checks shape and family narrowing; `number` and `string` do not enforce integer ranges or nonblank values at runtime. These types alone are not validation of an untrusted server response.

## Versioning and verification

Version 1 accepts only the documented shape. Renaming fields, adding fields or enum variants, or changing operand meaning requires a new contract version and an explicit client migration; do not reinterpret existing v1 fixtures. Unsupported versions are rejected. Wire schema versions belong to the presentation contract, not the domain or assessment rules.

The backend loads the shared fixtures as test resources. Contract tests use the Spring-configured mapper to verify variant selection, JSON round trips, explicit domain projection, solution exclusion, and malformed input rejection. The web's `typecheck` script checks valid variants and expected compile failures for invalid combinations; `build` runs that check, including in CI.

```bash
gradle -p services/api --no-daemon test
npm --prefix apps/web run typecheck
npm --prefix apps/web run build
```

Use Gradle 9.8.1, JDK 27, Node 24, and npm 12.2.0 as specified by the repository. This change adds no dependencies, endpoints, mathematical solvers, or runtime integrations.
