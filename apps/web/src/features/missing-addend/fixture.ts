// SPDX-License-Identifier: AGPL-3.0-or-later
import type { Difficulty, MissingAddendExercise, MissingAddendPresentation, VisualModel } from '../../contracts/exercise.ts'

const VISUAL_MODELS: readonly VisualModel[] = ['BLOCKS', 'NUMBER_LINE', 'TENS_AND_ONES']
const DIFFICULTIES: readonly Difficulty[] = ['EASY', 'MEDIUM', 'HARD']
const PRESENTATIONS: readonly MissingAddendPresentation[] = [
  'TOTAL_EQUALS_KNOWN_PLUS_MISSING',
  'KNOWN_PLUS_MISSING_EQUALS_TOTAL',
  'MISSING_PLUS_KNOWN_EQUALS_TOTAL',
]

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isOneOf<T extends string>(options: readonly T[], value: unknown): value is T {
  return (options as readonly unknown[]).includes(value)
}

function isCount(value: unknown): value is number {
  return Number.isSafeInteger(value) && (value as number) >= 0
}

/**
 * Narrows a trusted repository fixture without overriding its values, so a fixture
 * edit fails loudly instead of being masked. Not an API-boundary validator.
 */
export function toMissingAddendExercise(value: unknown): MissingAddendExercise {
  if (!isRecord(value) || !isRecord(value.operands)) throw new TypeError('Missing-addend fixture must be an object with operands')
  const { exerciseId, schemaVersion, type, visualModel, difficulty, presentation, operands } = value
  const { total, knownAddend } = operands
  if (typeof exerciseId !== 'string' || exerciseId === '') throw new TypeError('Invalid exerciseId')
  if (schemaVersion !== 1) throw new TypeError('Unsupported schemaVersion')
  if (type !== 'MISSING_ADDEND') throw new TypeError('Fixture is not a missing-addend exercise')
  if (!isOneOf(VISUAL_MODELS, visualModel)) throw new TypeError('Invalid visualModel')
  if (!isOneOf(DIFFICULTIES, difficulty)) throw new TypeError('Invalid difficulty')
  if (!isOneOf(PRESENTATIONS, presentation)) throw new TypeError('Invalid presentation')
  if (!isCount(total) || !isCount(knownAddend) || knownAddend > total) throw new TypeError('Invalid operands')
  return { exerciseId, schemaVersion, type, visualModel, difficulty, presentation, operands: { total, knownAddend } }
}
