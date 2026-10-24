// SPDX-License-Identifier: AGPL-3.0-or-later
import { useCallback, useReducer } from 'react'
import { completionReducer, emptyCompletion } from './attempt-completion.ts'
import type { CompletedTrace } from './attempt-completion.ts'
import type { SpeechRecord } from '../recording/speech-record.ts'

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
    dispatch({ kind: 'complete', attemptId: crypto.randomUUID(), exerciseId, trace })
  }, [])
  const reset = useCallback((exerciseId: string) => dispatch({ kind: 'reset', exerciseId }), [])
  const recordSpeech = useCallback((attemptId: string, record: SpeechRecord | null) => {
    dispatch({ kind: 'speech', attemptId, record })
  }, [])
  return { state, selectExercise, complete, reset, recordSpeech }
}

export type AttemptCompletionController = ReturnType<typeof useAttemptCompletion>
