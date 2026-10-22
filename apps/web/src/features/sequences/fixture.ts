// SPDX-License-Identifier: AGPL-3.0-or-later
import type { SequenceExercise } from '../../contracts/exercise.ts'
import { DIFFICULTIES, isOneOf, isRecord } from '../shared/fixture-guards.ts'
import { isInt32 } from './attempt.ts'

const MAX_TERMS = 20

/**
 * Narrows a trusted repository fixture without overriding its values, so a fixture
 * edit fails loudly instead of being masked. Not an API-boundary validator or solver.
 */
export function toSequenceExercise(value: unknown): SequenceExercise {
  if (!isRecord(value) || !isRecord(value.operands)) throw new TypeError('Sequence fixture must be an object with operands')
  const { exerciseId, schemaVersion, type, visualModel, difficulty, operands } = value
  const { terms } = operands
  if (typeof exerciseId !== 'string' || exerciseId.trim() === '') throw new TypeError('Invalid exerciseId')
  if (schemaVersion !== 1) throw new TypeError('Unsupported schemaVersion')
  if (type !== 'SEQUENCE') throw new TypeError('Fixture is not a sequence exercise')
  if (visualModel !== 'NUMBER_LINE') throw new TypeError('Sequence screen requires the NUMBER_LINE visual model')
  if (!isOneOf(DIFFICULTIES, difficulty)) throw new TypeError('Invalid difficulty')
  if (!Array.isArray(terms) || terms.length < 2 || terms.length > MAX_TERMS || !terms.every(isInt32)) {
    throw new TypeError('Invalid terms')
  }
  const [first, second, ...rest] = terms as number[]
  return { exerciseId, schemaVersion, type, visualModel, difficulty, operands: { terms: [first, second, ...rest] } }
}
