// SPDX-License-Identifier: AGPL-3.0-or-later
import type { SubtractionExercise } from '../../contracts/exercise.ts'
import { DIFFICULTIES, isOneOf, isRecord } from '../shared/fixture-guards.ts'

/** Trusted local demo fixtures only; this screen renders at most 99 pieces. */
export function toSubtractionExercise(value: unknown): SubtractionExercise {
  if (!isRecord(value) || !isRecord(value.operands)) throw new TypeError('Invalid subtraction fixture')
  const { exerciseId, schemaVersion, type, visualModel, difficulty, operands } = value
  const { minuend, subtrahend } = operands
  const count = (n: unknown): n is number => Number.isInteger(n) && (n as number) >= 0 && (n as number) <= 99
  if (typeof exerciseId !== 'string' || exerciseId.trim() === '' || schemaVersion !== 1 || type !== 'SUBTRACTION'
    || visualModel !== 'TENS_AND_ONES' || !isOneOf(DIFFICULTIES, difficulty)
    || !count(minuend) || !count(subtrahend) || subtrahend > minuend) throw new TypeError('Unsupported subtraction fixture')
  return { exerciseId, schemaVersion, type, visualModel, difficulty, operands: { minuend, subtrahend } }
}
