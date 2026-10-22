// SPDX-License-Identifier: AGPL-3.0-or-later
import type { ActionTracePayload, ActionEvent } from '../../src/contracts/action.ts'
import type { ActionTracePayloadV2, ActionEventV2 } from '../../src/contracts/action-v2.ts'
import fixture from '../../../../contracts/actions/v2/fixtures/subtraction.json'

const metadata = { step: 1, occurredAt: '2026-01-01T00:00:00Z' }
const exchange = { ...metadata, kind: 'decomposeTen' } as const satisfies ActionEventV2
const compose = { ...metadata, kind: 'composeTen' } as const satisfies ActionEventV2
const v2: ActionTracePayloadV2 = { schemaVersion: 2, initialValue: 53, events: [exchange, compose] }
// @ts-expect-error Exchanges must not leak into v1.
const v1Event: ActionEvent = exchange
// @ts-expect-error Versions are not interchangeable.
const v1: ActionTracePayload = v2
// @ts-expect-error An exchange cannot carry a magnitude.
const magnitude: ActionEventV2 = { ...exchange, value: 10 }
// @ts-expect-error Spreading a movement cannot sneak value into a canje.
const spread: ActionEventV2 = { ...metadata, value: 1, kind: 'composeTen' }
const shape: { schemaVersion: number; initialValue: number; events: readonly { kind: string; step: number; occurredAt: string; value?: number }[] } = fixture
// @ts-expect-error Public traces are readonly.
v2.events.push(exchange)
void [v1Event, v1, magnitude, spread, shape]
