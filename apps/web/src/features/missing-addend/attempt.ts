// SPDX-License-Identifier: AGPL-3.0-or-later
import type { ActionEvent, ActionTracePayload } from '../../contracts/action.ts'

export const MAX_BLOCKS = 10

export interface AttemptState {
  readonly count: number
  readonly completed: boolean
  readonly trace: ActionTracePayload
}

export type AttemptAction =
  | { readonly kind: 'placeBlock' | 'removeBlock' | 'finishAttempt'; readonly occurredAt: string }
  | { readonly kind: 'reset' }

export function emptyAttempt(): AttemptState {
  return Object.freeze({
    count: 0,
    completed: false,
    trace: Object.freeze({ schemaVersion: 1, initialValue: 0, events: Object.freeze([]) }),
  })
}

/** Only confirmed semantic actions enter the trace; pointer positions never do. */
export function attemptReducer(state: AttemptState, action: AttemptAction): AttemptState {
  if (action.kind === 'reset') return emptyAttempt()
  if (state.completed) return state
  if (action.kind === 'placeBlock' && state.count === MAX_BLOCKS) return state
  if (action.kind === 'removeBlock' && state.count === 0) return state

  const metadata = { step: state.trace.events.length + 1, occurredAt: action.occurredAt }
  const event: ActionEvent = action.kind === 'finishAttempt'
    ? { ...metadata, kind: 'finishAttempt' }
    : { ...metadata, kind: action.kind, value: 1 }
  const count = state.count + (action.kind === 'placeBlock' ? 1 : action.kind === 'removeBlock' ? -1 : 0)
  return Object.freeze({
    count,
    completed: action.kind === 'finishAttempt',
    trace: Object.freeze({
      ...state.trace,
      events: Object.freeze([...state.trace.events, Object.freeze(event)]),
    }),
  })
}
