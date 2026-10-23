// SPDX-License-Identifier: AGPL-3.0-or-later
import { useCallback, useReducer } from 'react'
import { completionReducer, emptyCompletion } from './attempt-completion.ts'
import type { CompletedTrace } from './attempt-completion.ts'

/**
 * One in-memory delivery for the selected exercise. Unmounting forgets it.
 * Callers name the exercise whose screen produced each callback, so a mismatch is rejected.
 */
export function useAttemptCompletion(initialExerciseId: string) {
  const [state, dispatch] = useReducer(completionReducer, initialExerciseId, emptyCompletion)
  const selectExercise = useCallback((exerciseId: string) => {
    dispatch({ kind: 'selectExercise', exerciseId })
  }, [])
  const complete = useCallback((exerciseId: string, trace: CompletedTrace) => {
    dispatch({ kind: 'complete', exerciseId, trace })
  }, [])
  const reset = useCallback((exerciseId: string) => dispatch({ kind: 'reset', exerciseId }), [])
  return { state, selectExercise, complete, reset }
}

export type AttemptCompletionController = ReturnType<typeof useAttemptCompletion>
