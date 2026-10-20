// SPDX-License-Identifier: AGPL-3.0-or-later
import type {
  MissingAddendExercise, SequenceExercise, SubtractionExercise,
} from '../../src/contracts/exercise'
import missingAddend from '../../../../contracts/exercises/v1/fixtures/missing-addend.json'
import sequence from '../../../../contracts/exercises/v1/fixtures/sequence.json'
import subtraction from '../../../../contracts/exercises/v1/fixtures/subtraction.json'

// JSON imports widen string literals, so enum values stay checked by the backend contract tests.
// These checks catch field renames, additions, and removals between the fixtures and the client types.
type Exact<A, B> = [A] extends [B] ? ([B] extends [A] ? true : false) : false
type SameKeys<Fixture, Variant> = Exact<keyof Fixture, keyof Variant>

const missingAddendKeys: SameKeys<typeof missingAddend, MissingAddendExercise> = true
const missingAddendOperands: SameKeys<
  typeof missingAddend.operands, MissingAddendExercise['operands']> = true
const sequenceKeys: SameKeys<typeof sequence, SequenceExercise> = true
const sequenceOperands: SameKeys<typeof sequence.operands, SequenceExercise['operands']> = true
const subtractionKeys: SameKeys<typeof subtraction, SubtractionExercise> = true
const subtractionOperands: SameKeys<
  typeof subtraction.operands, SubtractionExercise['operands']> = true

const numericOperands: [
  typeof missingAddend.operands extends { total: number; knownAddend: number } ? true : false,
  typeof sequence.operands extends { terms: number[] } ? true : false,
  typeof subtraction.operands extends { minuend: number; subtrahend: number } ? true : false,
] = [true, true, true]

void [
  missingAddendKeys, missingAddendOperands, sequenceKeys, sequenceOperands,
  subtractionKeys, subtractionOperands, numericOperands,
]
