// SPDX-License-Identifier: AGPL-3.0-or-later
import descendingFixture from '../../../../../contracts/exercises/v1/fixtures/sequence.json' with { type: 'json' }
import type { SequenceExercise } from '../../contracts/exercise.ts'

/** Narrows a trusted repository fixture; not an API-boundary validator or solver. */
export function toSequenceExercise(value: unknown): SequenceExercise {
  if (typeof value !== 'object' || value === null) throw new TypeError('Invalid sequence fixture')
  const candidate = value as Partial<SequenceExercise>
  const terms = candidate.operands?.terms
  if (candidate.schemaVersion !== 1 || candidate.type !== 'SEQUENCE' || candidate.visualModel !== 'NUMBER_LINE'
    || typeof candidate.exerciseId !== 'string' || candidate.exerciseId.trim() === ''
    || !['EASY', 'MEDIUM', 'HARD'].includes(candidate.difficulty ?? '')
    || !Array.isArray(terms) || terms.length < 2 || terms.length > 20
    || !terms.every(term => Number.isInteger(term) && term >= -2147483648 && term <= 2147483647)) {
    throw new TypeError('Invalid sequence fixture')
  }
  const [first, second, ...rest] = terms
  return {
    exerciseId: candidate.exerciseId, schemaVersion: 1, type: 'SEQUENCE', visualModel: 'NUMBER_LINE',
    difficulty: candidate.difficulty!, operands: { terms: [first, second, ...rest] },
  }
}

export const descendingExercise = toSequenceExercise(descendingFixture)
export const ascendingExercise: SequenceExercise = {
  exerciseId: 'sequence-10-12', schemaVersion: 1, type: 'SEQUENCE', visualModel: 'NUMBER_LINE',
  difficulty: 'EASY', operands: { terms: [10, 12] },
}
