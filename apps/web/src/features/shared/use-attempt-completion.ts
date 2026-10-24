// SPDX-License-Identifier: AGPL-3.0-or-later
import { useCallback, useReducer } from 'react'
import { completionReducer, emptyCompletion, newAttemptId } from './attempt-completion.ts'
import type { AttemptObservation, CompletedTrace, CompletionAction, CompletionState } from './attempt-completion.ts'

export interface AttemptCompletionController<R extends AttemptObservation = AttemptObservation> {
  readonly state: CompletionState<R>
  readonly selectExercise: (exerciseId: string) => void
  readonly complete: (exerciseId: string, trace: CompletedTrace) => void
  readonly reset: (exerciseId: string) => void
  readonly recordSpeech: (attemptId: string, record: R | null) => void
}

/**
 * One in-memory delivery for the selected exercise. Unmounting forgets it.
 * Callers name the exercise whose screen produced each callback, so a mismatch is rejected.
 */
export function useAttemptCompletion<R extends AttemptObservation = AttemptObservation>(
  initialExerciseId: string,
): AttemptCompletionController<R> {
  const [state, dispatch] = useReducer<CompletionState<R>, string, [CompletionAction<R>]>(
    completionReducer, initialExerciseId, emptyCompletion)
  const selectExercise = useCallback((exerciseId: string) => {
    dispatch({ kind: 'selectExercise', exerciseId })
  }, [])
  const complete = useCallback((exerciseId: string, trace: CompletedTrace) => {
    dispatch({ kind: 'complete', attemptId: newAttemptId(), exerciseId, trace })
  }, [])
  const reset = useCallback((exerciseId: string) => dispatch({ kind: 'reset', exerciseId }), [])
  const recordSpeech = useCallback((attemptId: string, record: R | null) => {
    dispatch({ kind: 'speech', attemptId, record })
  }, [])
  return { state, selectExercise, complete, reset, recordSpeech }
}
