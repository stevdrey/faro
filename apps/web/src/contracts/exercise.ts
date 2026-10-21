// SPDX-License-Identifier: AGPL-3.0-or-later
export type VisualModel = 'BLOCKS' | 'NUMBER_LINE' | 'TENS_AND_ONES'
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD'
export type MissingAddendPresentation =
  | 'TOTAL_EQUALS_KNOWN_PLUS_MISSING'
  | 'KNOWN_PLUS_MISSING_EQUALS_TOTAL'
  | 'MISSING_PLUS_KNOWN_EQUALS_TOTAL'

interface ExerciseMetadata {
  readonly exerciseId: string
  readonly schemaVersion: 1
  readonly visualModel: VisualModel
  readonly difficulty: Difficulty
}

export type MissingAddendExercise = ExerciseMetadata & {
  readonly type: 'MISSING_ADDEND'
  readonly operands: { readonly total: number; readonly knownAddend: number }
  readonly presentation: MissingAddendPresentation
}

export type SequenceExercise = ExerciseMetadata & {
  readonly type: 'SEQUENCE'
  readonly operands: { readonly terms: readonly [number, number, ...number[]] }
}

export type SubtractionExercise = ExerciseMetadata & {
  readonly type: 'SUBTRACTION'
  readonly operands: { readonly minuend: number; readonly subtrahend: number }
}

/** Public presentation only. Runtime validation of untrusted JSON belongs at the API boundary. */
export type Exercise = MissingAddendExercise | SequenceExercise | SubtractionExercise
export type ExerciseType = Exercise['type']
