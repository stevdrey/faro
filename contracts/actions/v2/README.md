# Semantic action contract, version 2

FARO-007 / [issue #24](https://github.com/stevdrey/faro/issues/24) adds explicit,
quantity-preserving exchanges. There is no HTTP endpoint, persistence or grading.

## Wire format and compatibility

The envelope contains exactly `schemaVersion: 2`, `initialValue` (signed 32-bit
integer), and `events` (ordered array). Each event has `kind`, consecutive `step`
from 1, and `occurredAt`. Timestamp rules and strict JSON binding are identical to
[v1](../v1/README.md): UTC `YYYY-MM-DDTHH:MM:SS[.fraction]Z`, one to nine fractional
digits, valid calendar dates, no coercions, unknown fields, duplicate keys, nulls
or trailing values. Equal or decreasing timestamps do not change event order.

| Event kind | Fields beyond metadata | Meaning |
|---|---|---|
| `placeBlock` | positive integer `value` | Add the magnitude to the available quantity. |
| `removeBlock` | positive integer `value` | Remove the magnitude from the available quantity. |
| `jumpLeft`, `jumpRight` | positive integer `value` | Existing scalar number-line movements. |
| `decomposeTen` | none | Exchange one available ten for ten available units. |
| `composeTen` | none | Exchange ten available units for one available ten. |
| `finishAttempt` | none | Complete without changing quantities. |

Structural events forbid `value`, including zero or null. `finishAttempt` is
optional, unique and terminal; an empty trace and immediate finish are valid.
Metadata and ordering validation do not establish that the requested pieces exist.

Java exposes `ActionTracePayloadV2` and the dedicated strict
`ActionTracePayloadV2Json` codec. TypeScript exposes readonly `ActionEventV2` and
`ActionTracePayloadV2` in `apps/web/src/contracts/action-v2.ts`. TypeScript types
alone do not validate arbitrary JSON. A future endpoint must explicitly use the
strict codec or an equivalent converter; ordinary request binding is insufficient.

V1 retains its original wire shape, event set and scalar semantics. It rejects v2
envelopes and exchange kinds. Conversion of a domain trace containing exchanges
to v1 fails explicitly; it never drops or translates exchanges into artificial
remove/place movements. Existing screens and callbacks continue emitting v1.
No persisted data or released consumers need migration. V2 is opt-in, and no
automatic upgrade or downgrade is introduced.

## Tens-and-ones replay

`TensAndOnesReplay.replay(initialValue, trace)` and web
`replayTensAndOnes(payload)` reconstruct `tens`, `ones`, `removedTens`,
`removedOnes` and `completed`. Start from a nonnegative signed 32-bit quantity:
available tens are integer division by 10, available units the remainder, and
both removed counts are zero. Unit counts are **not normalized after exchanges**.

This visual model restricts movements to exactly one piece: `value: 10` for a
ten or `value: 1` for a unit. Removal transfers that piece from available to
removed; placement transfers it back. A source must contain the requested piece;
having sufficient total value does not cause automatic borrowing. Exchanges
operate only on available pieces. Composition requires at least ten available
units, decomposition at least one available ten. Jumps and other movement
magnitudes are rejected by this replay, though they remain valid generic v2
event shapes. Every accepted operation conserves the initial total across trays.
No piece IDs, coordinates, answer, audio or transcript are carried.

Java uses the existing immutable `ActionTrace` for order and finalization checks.
The web replay validates metadata and finalization and shares transition rules
with the interactive reducer. Invalid traces fail without returning partial
results. Java's scalar `ActionReplay` remains usable for the legacy events and
explicitly directs exchange traces to `TensAndOnesReplay`.

The shared synthetic [subtraction fixture](fixtures/subtraction.json) removes two
tens, decomposes a ten, reverses that exchange, decomposes again, removes eight
units, returns/removes one unit, and finishes. Both languages reconstruct two
available tens and five units, with two tens and eight units removed. Prefixes
after the first two removals and first exchange contain 33 available items, in
different representations. These are replay facts, never an assessment.

## Verification

Run the Java tests, web unit tests, typecheck and browser suite described in the
[FARO-007 verification summary](../../../docs/verification/subtraction-tens-and-ones.md).
They cover shared-fixture replay, corrections, conservation, both exchanges,
strict decoding, immutable evidence, v1 rejection and interaction accessibility.
