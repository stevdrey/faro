// SPDX-License-Identifier: AGPL-3.0-or-later
import type { Exercise, ExerciseType } from '../../src/contracts/exercise'

const metadata = {
  exerciseId: 'synthetic-exercise', schemaVersion: 1, difficulty: 'EASY',
} as const

const missingAddend = {
  ...metadata, type: 'MISSING_ADDEND', visualModel: 'BLOCKS',
  operands: { total: 18, knownAddend: 12 },
} as const satisfies Exercise
const sequence = {
  ...metadata, type: 'SEQUENCE', visualModel: 'NUMBER_LINE',
  operands: { terms: [30, 28] },
} as const satisfies Exercise
const subtraction = {
  ...metadata, type: 'SUBTRACTION', visualModel: 'TENS_AND_ONES',
  operands: { minuend: 53, subtrahend: 28 },
} as const satisfies Exercise

// @ts-expect-error The discriminator must agree with the operands.
const mismatched: Exercise = { ...missingAddend, operands: subtraction.operands }
// @ts-expect-error Version 1 is the only supported wire version.
const unsupported: Exercise = { ...sequence, schemaVersion: 2 }
// @ts-expect-error A sequence needs at least two visible terms.
const tooShort: Exercise = { ...sequence, operands: { terms: [30] } }
// @ts-expect-error Unknown difficulty categories are not part of the contract.
const difficulty: Exercise = { ...subtraction, difficulty: 'EXPERT' }
// @ts-expect-error The public payload never contains an expected result.
const solution: Exercise = { ...missingAddend, expectedResult: 0 }
// @ts-expect-error Required metadata cannot be omitted.
const incomplete: Exercise = { type: 'SEQUENCE', operands: { terms: [30, 28] } }
// @ts-expect-error Operand values must be numeric.
const stringOperand: Exercise = { ...subtraction, operands: { minuend: '53', subtrahend: 28 } }
// @ts-expect-error Unknown exercise types cannot enter the public union.
const unknownType: ExerciseType = 'MULTIPLICATION'

// Check that clients can narrow by type and access only that family's fields.
function visibleInput(exercise: Exercise): number {
  switch (exercise.type) {
    case 'MISSING_ADDEND': return exercise.operands.total
    case 'SEQUENCE': return exercise.operands.terms[0]
    case 'SUBTRACTION': return exercise.operands.minuend
    default: {
      const exhaustive: never = exercise
      return exhaustive
    }
  }
}

void [missingAddend, sequence, subtraction, visibleInput]
