// SPDX-License-Identifier: AGPL-3.0-or-later
import type { ActionTracePayload } from '../../contracts/action.ts'
import type { ActionTracePayloadV2 } from '../../contracts/action-v2.ts'
import type { SpeechRecord } from '../recording/speech-record.ts'

export type CompletedTrace = ActionTracePayload | ActionTracePayloadV2

export interface CompletedAttempt {
  readonly attemptId: string
  readonly exerciseId: string
  readonly trace: CompletedTrace
}

export interface CompletionState {
  readonly exerciseId: string
  readonly completedAttempt: CompletedAttempt | null
  readonly speechRecord: SpeechRecord | null
}

export type CompletionAction =
  | { readonly kind: 'selectExercise'; readonly exerciseId: string }
  | { readonly kind: 'complete'; readonly attemptId: string; readonly exerciseId: string; readonly trace: CompletedTrace }
  | { readonly kind: 'reset'; readonly exerciseId: string }
  | { readonly kind: 'speech'; readonly attemptId: string; readonly record: SpeechRecord | null }

export function emptyCompletion(exerciseId: string): CompletionState {
  return Object.freeze({ exerciseId, completedAttempt: null, speechRecord: null })
}

/** Retains the deeply frozen trace supplied by an activity reducer, without grading or copying it. */
export function completionReducer(state: CompletionState, action: CompletionAction): CompletionState {
  if (action.kind === 'speech') {
    if (!state.completedAttempt || action.attemptId !== state.completedAttempt.attemptId
      || (action.record && action.record.attemptId !== action.attemptId) || action.record === state.speechRecord) return state
    return Object.freeze({ ...state, speechRecord: action.record })
  }
  if (action.kind === 'selectExercise') {
    return action.exerciseId === state.exerciseId ? state : emptyCompletion(action.exerciseId)
  }
  // A callback from a screen that is no longer selected cannot affect the current exercise.
  if (action.exerciseId !== state.exerciseId) return state
  if (action.kind === 'reset') return state.completedAttempt === null ? state : emptyCompletion(state.exerciseId)
  if (state.completedAttempt !== null || action.trace.events.at(-1)?.kind !== 'finishAttempt') return state
  return Object.freeze({
    exerciseId: state.exerciseId,
    completedAttempt: Object.freeze({ attemptId: action.attemptId, exerciseId: state.exerciseId, trace: action.trace }),
    speechRecord: null,
  })
}
