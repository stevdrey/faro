// SPDX-License-Identifier: AGPL-3.0-or-later
import type { ActionTracePayload } from '../../contracts/action.ts'
import type { ActionTracePayloadV2 } from '../../contracts/action-v2.ts'

export type CompletedTrace = ActionTracePayload | ActionTracePayloadV2

export interface CompletedAttempt {
  readonly attemptId: string
  readonly exerciseId: string
  readonly trace: CompletedTrace
}

/** The only shape completion needs from a speech observation; the recording feature owns the rest. */
export interface AttemptObservation {
  readonly attemptId: string
}

export interface CompletionState<R extends AttemptObservation = AttemptObservation> {
  readonly exerciseId: string
  readonly completedAttempt: CompletedAttempt | null
  readonly speechRecord: R | null
}

export type CompletionAction<R extends AttemptObservation = AttemptObservation> =
  | { readonly kind: 'selectExercise'; readonly exerciseId: string }
  | { readonly kind: 'complete'; readonly attemptId: string; readonly exerciseId: string; readonly trace: CompletedTrace }
  | { readonly kind: 'reset'; readonly exerciseId: string }
  | { readonly kind: 'speech'; readonly attemptId: string; readonly record: R | null }

/** getRandomValues, unlike randomUUID, also works outside a secure context (for example plain HTTP on a LAN). */
export function newAttemptId(): string {
  return Array.from(crypto.getRandomValues(new Uint8Array(16)), byte => byte.toString(16).padStart(2, '0')).join('')
}

export function emptyCompletion<R extends AttemptObservation = AttemptObservation>(exerciseId: string): CompletionState<R> {
  return Object.freeze({ exerciseId, completedAttempt: null, speechRecord: null })
}

/** Retains the deeply frozen trace supplied by an activity reducer, without grading or copying it. */
export function completionReducer<R extends AttemptObservation>(state: CompletionState<R>, action: CompletionAction<R>): CompletionState<R> {
  if (action.kind === 'speech') {
    if (!state.completedAttempt || action.attemptId !== state.completedAttempt.attemptId
      || (action.record && action.record.attemptId !== action.attemptId) || action.record === state.speechRecord) return state
    return Object.freeze({ ...state, speechRecord: action.record })
  }
  if (action.kind === 'selectExercise') {
    return action.exerciseId === state.exerciseId ? state : emptyCompletion<R>(action.exerciseId)
  }
  // A callback from a screen that is no longer selected cannot affect the current exercise.
  if (action.exerciseId !== state.exerciseId) return state
  if (action.kind === 'reset') return state.completedAttempt === null ? state : emptyCompletion<R>(state.exerciseId)
  if (state.completedAttempt !== null || action.trace.events.at(-1)?.kind !== 'finishAttempt') return state
  return Object.freeze({
    exerciseId: state.exerciseId,
    completedAttempt: Object.freeze({ attemptId: action.attemptId, exerciseId: state.exerciseId, trace: action.trace }),
    speechRecord: null,
  })
}
