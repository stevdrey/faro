// SPDX-License-Identifier: AGPL-3.0-or-later
import assert from 'node:assert/strict'
import test from 'node:test'
import { exerciseFor, initialSelection, isExample, sequenceExamples } from '../../src/features/shared/activity-selection.ts'
import type { Activity, Example } from '../../src/features/shared/activity-selection.ts'

const expected: Record<Activity, Record<Example, string>> = {
  blocks: { descending: 'missing-addend-known-first-18-12', ascending: 'missing-addend-known-first-18-12' },
  sequences: { descending: 'sequence-30-28', ascending: 'sequence-10-12' },
  subtraction: { descending: 'subtraction-53-28', ascending: 'subtraction-53-28' },
}

test('every activity/example selection maps to exactly one shown exercise', () => {
  for (const activity of Object.keys(expected) as Activity[]) {
    for (const example of Object.keys(sequenceExamples) as Example[]) {
      assert.equal(exerciseFor({ activity, example }).exerciseId, expected[activity][example], `${activity}/${example}`)
    }
  }
  assert.equal(exerciseFor({ activity: 'sequences', example: 'ascending' }), sequenceExamples.ascending)
})

test('the initial selection is the blocks exercise', () => {
  assert.deepEqual(initialSelection, { activity: 'blocks', example: 'descending' })
  assert.ok(Object.isFrozen(initialSelection))
  assert.equal(exerciseFor(initialSelection).type, 'MISSING_ADDEND')
})

test('only known sequence examples are accepted from the picker', () => {
  assert.ok(isExample('descending') && isExample('ascending'))
  for (const value of ['', 'Descending', 'toString', '__proto__', 'constructor']) assert.equal(isExample(value), false, value)
})
