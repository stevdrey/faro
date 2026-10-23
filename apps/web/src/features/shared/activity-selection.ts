// SPDX-License-Identifier: AGPL-3.0-or-later
import type { Exercise, SequenceExercise } from '../../contracts/exercise.ts'
import { demoExercise } from '../missing-addend/exercise.ts'
import { ascendingExercise, descendingExercise } from '../sequences/exercise.ts'
import { subtractionExercise } from '../subtraction/exercise.ts'

export type Activity = 'blocks' | 'sequences' | 'subtraction'
export type Example = 'descending' | 'ascending'

export interface ActivitySelection {
  readonly activity: Activity
  readonly example: Example
}

export const initialSelection: ActivitySelection = Object.freeze({ activity: 'blocks', example: 'descending' })

export const sequenceExamples: Readonly<Record<Example, SequenceExercise>> = Object.freeze({
  descending: descendingExercise,
  ascending: ascendingExercise,
})

export function isExample(value: string): value is Example {
  return Object.hasOwn(sequenceExamples, value)
}

/** The single mapping from what is selected to the exercise that is shown and completed. */
export function exerciseFor({ activity, example }: ActivitySelection): Exercise {
  return activity === 'blocks' ? demoExercise
    : activity === 'subtraction' ? subtractionExercise
    : sequenceExamples[example]
}
