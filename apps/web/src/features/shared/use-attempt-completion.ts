// SPDX-License-Identifier: AGPL-3.0-or-later
import { useCallback, useReducer } from 'react'
import { completionReducer, emptyCompletion } from './attempt-completion.ts'
import type { CompletedTrace } from './attempt-completion.ts'

/** One in-memory delivery for the selected exercise. Unmounting forgets it. */
export function useAttemptCompletion(initialExerciseId: string) {
  const [state, dispatch] = useReducer(completionReducer, initialExerciseId, emptyCompletion)
  const exerciseId = state.exerciseId
  const selectExercise = useCallback((nextExerciseId: string) => {
    dispatch({ kind: 'selectExercise', exerciseId: nextExerciseId })
  }, [])
  const onComplete = useCallback((trace: CompletedTrace) => {
    dispatch({ kind: 'complete', exerciseId, trace })
  }, [exerciseId])
  const onReset = useCallback(() => dispatch({ kind: 'reset', exerciseId }), [exerciseId])
  return { state, selectExercise, onComplete, onReset }
}

export type AttemptCompletionController = ReturnType<typeof useAttemptCompletion>
