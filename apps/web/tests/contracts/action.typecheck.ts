// SPDX-License-Identifier: AGPL-3.0-or-later
import type { ActionEvent, ActionKind, ActionTracePayload, FinishAttempt } from '../../src/contracts/action'

const metadata = { step: 1, occurredAt: '2026-01-01T00:00:00Z' } as const
const place = { ...metadata, kind: 'placeBlock', value: 10 } as const satisfies ActionEvent
const remove = { ...metadata, kind: 'removeBlock', value: 4 } as const satisfies ActionEvent
const left = { ...metadata, kind: 'jumpLeft', value: 2 } as const satisfies ActionEvent
const right = { ...metadata, kind: 'jumpRight', value: 2 } as const satisfies ActionEvent
const finish = { ...metadata, kind: 'finishAttempt' } as const satisfies ActionEvent
const trace = { schemaVersion: 1, initialValue: 0, events: [place] } as const satisfies ActionTracePayload
const empty = { schemaVersion: 1, initialValue: 28, events: [] } as const satisfies ActionTracePayload

// @ts-expect-error A movement must carry its magnitude.
const noValue: ActionEvent = { ...metadata, kind: 'jumpLeft' }
// @ts-expect-error Finishing does not carry a magnitude.
const finishValue: ActionEvent = { ...finish, value: 0 }
// @ts-expect-error A spread must not carry a movement magnitude into a finish event.
const finishFromSpread: ActionEvent = { ...place, kind: 'finishAttempt', step: 2 }
const intermediateFinish = { ...place, kind: 'finishAttempt', step: 2 } as const
// @ts-expect-error Structural assignment must reject a finish event retaining a magnitude.
const finishFromVariable: ActionEvent = intermediateFinish
// @ts-expect-error The concrete finish variant must reject the same intermediate object.
const finishVariantFromVariable: FinishAttempt = intermediateFinish
const intermediateValidFinish = { ...metadata, kind: 'finishAttempt' } as const
const validFinishFromVariable: FinishAttempt = intermediateValidFinish
const validFinishEvent: ActionEvent = validFinishFromVariable
// @ts-expect-error Unknown event kinds cannot enter the contract.
const unknown: ActionKind = 'dragSprite'
// @ts-expect-error Step is required for every event.
const noStep: ActionEvent = { kind: 'finishAttempt', occurredAt: metadata.occurredAt }
// @ts-expect-error Timestamps are strings, never epoch numbers.
const numericTime: ActionEvent = { ...place, occurredAt: 0 }
// @ts-expect-error Magnitudes are numeric.
const stringValue: ActionEvent = { ...remove, value: '4' }
// @ts-expect-error Version 1 is the only supported version.
const future: ActionTracePayload = { ...trace, schemaVersion: 2 }
// @ts-expect-error The initial value is explicit.
const noInitial: ActionTracePayload = { schemaVersion: 1, events: [] }

function movement(event: ActionEvent): number {
  switch (event.kind) {
    case 'placeBlock':
    case 'jumpRight': return event.value
    case 'removeBlock':
    case 'jumpLeft': return -event.value
    case 'finishAttempt':
      // @ts-expect-error A narrowed finish event has no numeric magnitude.
      const finishMagnitude: number = event.value
      void finishMagnitude
      return 0
    default: {
      const exhaustive: never = event
      return exhaustive
    }
  }
}

function readonlyContract(payload: ActionTracePayload): void {
  // @ts-expect-error A trace cannot be extended in place.
  payload.events.push(finish)
  // @ts-expect-error The initial value cannot be changed in place.
  payload.initialValue = 99
  // @ts-expect-error Event metadata is readonly.
  payload.events[0].step = 99
}

void [
  place, remove, left, right, finish, trace, empty, movement, readonlyContract,
  validFinishFromVariable, validFinishEvent,
]
