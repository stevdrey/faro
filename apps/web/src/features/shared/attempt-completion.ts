// SPDX-License-Identifier: AGPL-3.0-or-later
import type { ActionTracePayload } from '../../contracts/action.ts'
import type { ActionTracePayloadV2 } from '../../contracts/action-v2.ts'

export type CompletedTrace = ActionTracePayload | ActionTracePayloadV2

export interface CompletedAttempt {
  readonly exerciseId: string
  readonly trace: CompletedTrace
}

export interface CompletionState {
  readonly exerciseId: string
  readonly completedAttempt: CompletedAttempt | null
}

export type CompletionAction =
  | { readonly kind: 'selectExercise'; readonly exerciseId: string }
  | { readonly kind: 'complete'; readonly exerciseId: string; readonly trace: CompletedTrace }
  | { readonly kind: 'reset'; readonly exerciseId: string }

export function emptyCompletion(exerciseId: string): CompletionState {
  return Object.freeze({ exerciseId, completedAttempt: null })
}

/** Retains the deeply frozen trace supplied by an activity reducer, without grading or copying it. */
export function completionReducer(state: CompletionState, action: CompletionAction): CompletionState {
  if (action.kind === 'selectExercise') {
    return action.exerciseId === state.exerciseId ? state : emptyCompletion(action.exerciseId)
  }
  // A callback from a screen that is no longer selected cannot affect the current exercise.
  if (action.exerciseId !== state.exerciseId) return state
  if (action.kind === 'reset') return state.completedAttempt === null ? state : emptyCompletion(state.exerciseId)
  if (state.completedAttempt !== null || action.trace.events.at(-1)?.kind !== 'finishAttempt') return state
  return Object.freeze({
    exerciseId: state.exerciseId,
    completedAttempt: Object.freeze({ exerciseId: state.exerciseId, trace: action.trace }),
  })
}
