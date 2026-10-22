// SPDX-License-Identifier: AGPL-3.0-or-later
import type { ActionEventV2, ActionTracePayloadV2 } from '../../contracts/action-v2.ts'

export interface Pieces {
  readonly tens: number
  readonly ones: number
  readonly removedTens: number
  readonly removedOnes: number
  readonly completed: boolean
}

export type PieceMove = { readonly kind: 'removeBlock' | 'placeBlock'; readonly value: 1 | 10 }
export type Operation = PieceMove | { readonly kind: 'decomposeTen' | 'composeTen' | 'finishAttempt'; readonly value?: never }
export type SubtractionAction = (Operation & { readonly occurredAt: string }) | { readonly kind: 'reset' }
export interface SubtractionAttempt extends Pieces { readonly trace: ActionTracePayloadV2 }

function initialPieces(initialValue: number): Pieces {
  if (!Number.isInteger(initialValue) || initialValue < 0 || initialValue > 2147483647) {
    throw new RangeError('Initial quantity must be a nonnegative signed 32-bit integer')
  }
  return Object.freeze({ tens: Math.floor(initialValue / 10), ones: initialValue % 10, removedTens: 0, removedOnes: 0, completed: false })
}

/** The same transition rules are used for replay and interactive exploration. */
function applyOperation(state: Pieces, action: Operation): Pieces | null {
  if (state.completed) return null
  let { tens, ones, removedTens, removedOnes } = state
  switch (action.kind) {
    case 'removeBlock':
    case 'placeBlock': {
      if (action.value !== 1 && action.value !== 10) return null
      const removing = action.kind === 'removeBlock'
      if (action.value === 10) {
        if ((removing ? tens : removedTens) === 0) return null
        tens += removing ? -1 : 1
        removedTens += removing ? 1 : -1
      } else {
        if ((removing ? ones : removedOnes) === 0) return null
        ones += removing ? -1 : 1
        removedOnes += removing ? 1 : -1
      }
      break
    }
    case 'decomposeTen':
      if (tens === 0) return null
      tens--; ones += 10
      break
    case 'composeTen':
      if (ones < 10) return null
      ones -= 10; tens++
      break
    case 'finishAttempt': break
    default: return null
  }
  return Object.freeze({ tens, ones, removedTens, removedOnes, completed: action.kind === 'finishAttempt' })
}

export function canOperate(state: Pieces, action: Operation): boolean {
  return applyOperation(state, action) !== null
}

export function emptySubtractionAttempt(initialValue: number): SubtractionAttempt {
  return Object.freeze({ ...initialPieces(initialValue), trace: Object.freeze({ schemaVersion: 2, initialValue, events: Object.freeze([]) }) })
}

export function subtractionAttemptReducer(state: SubtractionAttempt, action: SubtractionAction): SubtractionAttempt {
  if (action.kind === 'reset') return emptySubtractionAttempt(state.trace.initialValue)
  const next = applyOperation(state, action)
  if (!next) return state
  const metadata = { step: state.trace.events.length + 1, occurredAt: action.occurredAt }
  const event: ActionEventV2 = Object.freeze(action.kind === 'removeBlock' || action.kind === 'placeBlock'
    ? { ...metadata, kind: action.kind, value: action.value }
    : { ...metadata, kind: action.kind })
  return Object.freeze({ ...next, trace: Object.freeze({ ...state.trace, events: Object.freeze([...state.trace.events, event]) }) })
}

/** Strict replay of a typed trace, including ordering, metadata and tray availability. */
export function replayTensAndOnes(trace: ActionTracePayloadV2): Pieces {
  if (trace.schemaVersion !== 2) throw new TypeError('Expected action schema version 2')
  let state = initialPieces(trace.initialValue)
  for (const [index, event] of trace.events.entries()) {
    const at = event.occurredAt
    if (event.step !== index + 1 || event.step > 2147483647 || typeof at !== 'string'
      || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,9})?Z$/.test(at)
      || !Number.isFinite(Date.parse(at)) || new Date(at).toISOString().slice(0, 19) !== at.slice(0, 19)) {
      throw new TypeError('Invalid event metadata')
    }
    if (event.kind === 'jumpLeft' || event.kind === 'jumpRight') throw new TypeError('Jumps are not piece movements')
    if (event.kind !== 'placeBlock' && event.kind !== 'removeBlock' && 'value' in event) throw new TypeError('Structural events have no value')
    const next = applyOperation(state, event as Operation)
    if (!next) throw new RangeError('Invalid piece operation')
    state = next
  }
  return state
}
