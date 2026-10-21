// SPDX-License-Identifier: AGPL-3.0-or-later
import type { ActionTracePayload, FinishAttempt, PlaceBlock } from '../../src/contracts/action'
import blocks from '../../../../contracts/actions/v1/fixtures/blocks.json'
import numberLine from '../../../../contracts/actions/v1/fixtures/number-line.json'
import subtraction from '../../../../contracts/actions/v1/fixtures/subtraction.json'
import incomplete from '../../../../contracts/actions/v1/fixtures/incomplete.json'

// JSON imports widen string literals. Backend tests check discriminator and schema-version values.
type Exact<A, B> = [A] extends [B] ? ([B] extends [A] ? true : false) : false
type Envelope = { schemaVersion: number; initialValue: number; events: readonly unknown[] }
type MovementShape = { kind: string; value: number; step: number; occurredAt: string }
type FinishShape = { kind: string; step: number; occurredAt: string; value?: never }

const envelopeKeys: [
  Exact<keyof typeof blocks, keyof ActionTracePayload>,
  Exact<keyof typeof numberLine, keyof ActionTracePayload>,
  Exact<keyof typeof subtraction, keyof ActionTracePayload>,
  Exact<keyof typeof incomplete, keyof ActionTracePayload>,
] = [true, true, true, true]

const envelopes: readonly Envelope[] = [blocks, numberLine, subtraction, incomplete]
const events: readonly (MovementShape | FinishShape)[] = [
  ...blocks.events, ...numberLine.events, ...subtraction.events, ...incomplete.events,
]
const eventKeys: [
  Exact<keyof typeof incomplete.events[number], keyof PlaceBlock>,
  Exact<keyof MovementShape, keyof PlaceBlock>,
  Exact<keyof FinishShape, keyof FinishAttempt>,
] = [true, true, true]

// Reject extra event fields even though structural assignment above allows them.
type SharedEvent = typeof blocks.events[number] | typeof numberLine.events[number]
  | typeof subtraction.events[number] | typeof incomplete.events[number]
type KeysOfUnion<T> = T extends unknown ? keyof T : never
const noExtraEventKeys: Exact<Exclude<KeysOfUnion<SharedEvent>, keyof MovementShape>, never> = true

void [envelopeKeys, envelopes, events, eventKeys, noExtraEventKeys]
