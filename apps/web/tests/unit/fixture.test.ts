// SPDX-License-Identifier: AGPL-3.0-or-later
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { toMissingAddendExercise } from '../../src/features/missing-addend/fixture.ts'

const fixturesDir = new URL('../../../../contracts/exercises/v1/fixtures/', import.meta.url)
const read = (name: string): unknown => JSON.parse(readFileSync(new URL(name, fixturesDir), 'utf8'))
const valid = read('missing-addend-known-first.json') as Record<string, unknown>

test('narrows every shared missing-addend fixture without changing its values', () => {
  for (const name of ['missing-addend.json', 'missing-addend-known-first.json', 'missing-addend-missing-first.json']) {
    const fixture = read(name)
    assert.deepEqual(toMissingAddendExercise(fixture), fixture)
  }
})

for (const [field, value] of [
  ['schemaVersion', 2],
  ['type', 'SEQUENCE'],
  ['visualModel', 'ABACUS'],
  ['difficulty', 'TRIVIAL'],
  ['presentation', 'MISSING_EQUALS_TOTAL'],
  ['exerciseId', ''],
  ['operands', { total: 18, knownAddend: 12.5 }],
  ['operands', { total: 12, knownAddend: 18 }],
  ['operands', { total: -1, knownAddend: 0 }],
] as const) {
  test(`rejects a fixture with invalid ${field} ${JSON.stringify(value)}`, () => {
    assert.throws(() => toMissingAddendExercise({ ...valid, [field]: value }), TypeError)
  })
}

test('rejects non-objects', () => {
  assert.throws(() => toMissingAddendExercise(null), TypeError)
  assert.throws(() => toMissingAddendExercise({ ...valid, operands: undefined }), TypeError)
})
