// SPDX-License-Identifier: AGPL-3.0-or-later
import assert from 'node:assert/strict'
import test from 'node:test'
import { emptySequenceAttempt, JUMP_SIZES, sequenceAttemptReducer, visibleJumps } from '../../src/features/sequences/attempt.ts'
import type { JumpKind, JumpSize, SequenceAttempt } from '../../src/features/sequences/attempt.ts'
import { ascendingExercise, descendingExercise, toSequenceExercise } from '../../src/features/sequences/exercise.ts'
import { readFileSync } from 'node:fs'

const occurredAt = '2026-01-01T00:00:00.000Z'
const move = (state: SequenceAttempt, kind: JumpKind, value: JumpSize) => sequenceAttemptReducer(state, { kind, value, occurredAt })
const finish = (state: SequenceAttempt) => sequenceAttemptReducer(state, { kind: 'finishAttempt', occurredAt })

test('uses the final visible term without altering the shared descending fixture', () => {
  const fixture: unknown = JSON.parse(readFileSync(new URL('../../../../contracts/exercises/v1/fixtures/sequence.json', import.meta.url), 'utf8'))
  assert.deepEqual(toSequenceExercise(fixture), fixture)
  for (const [exercise, initialValue] of [[descendingExercise, 28], [ascendingExercise, 12]] as const) {
    const state = emptySequenceAttempt(exercise.operands.terms.at(-1)!)
    assert.deepEqual(state, { position: initialValue, completed: false, trace: { schemaVersion: 1, initialValue, events: [] } })
  }
})

for (const value of JUMP_SIZES) {
  for (const kind of ['jumpLeft', 'jumpRight'] as const) {
    test(`${kind} records magnitude ${value} and replays to its visible destination`, () => {
      const initial = emptySequenceAttempt(28)
      const result = move(initial, kind, value)
      const position = 28 + (kind === 'jumpLeft' ? -value : value)
      assert.equal(result.position, position)
      assert.deepEqual(result.trace.events, [{ kind, value, step: 1, occurredAt }])
      assert.deepEqual(visibleJumps(result.trace), [{ from: 28, to: position, event: result.trace.events[0] }])
      assert.equal(initial.trace.events.length, 0)
    })
  }
}

test('preserves corrections, repeated paths, negative positions and supplied timestamp order', () => {
  const initial = emptySequenceAttempt(2)
  const first = move(initial, 'jumpLeft', 5)
  const second = sequenceAttemptReducer(first, { kind: 'jumpRight', value: 2, occurredAt: '2025-12-31T23:59:59.000Z' })
  const third = move(second, 'jumpLeft', 2)
  assert.equal(third.position, -3)
  assert.deepEqual(visibleJumps(third.trace).map(({ from, to }) => [from, to]), [[2, -3], [-3, -1], [-1, -3]])
  assert.deepEqual(third.trace.events.map(event => event.step), [1, 2, 3])
  assert.equal(third.trace.events[1].occurredAt, '2025-12-31T23:59:59.000Z')
  assert.equal(first.trace.events.length, 1)
})

test('rejects overflow without adding a jump and can move back from either integer limit', () => {
  const maximum = emptySequenceAttempt(2147483647)
  const minimum = emptySequenceAttempt(-2147483648)
  for (const size of JUMP_SIZES) {
    assert.equal(move(maximum, 'jumpRight', size), maximum)
    assert.equal(move(minimum, 'jumpLeft', size), minimum)
    assert.equal(move(maximum, 'jumpLeft', size).position, 2147483647 - size)
    assert.equal(move(minimum, 'jumpRight', size).position, -2147483648 + size)
  }
})

for (const moves of [0, 1, 3]) {
  test(`finishes after ${moves} moves with one terminal event, freezes evidence and resets`, () => {
    let state = emptySequenceAttempt(28)
    for (let index = 0; index < moves; index++) state = move(state, 'jumpLeft', 2)
    const completed = finish(state)
    assert.equal(completed.completed, true)
    assert.equal(completed.position, state.position)
    assert.deepEqual(completed.trace.events.at(-1), { kind: 'finishAttempt', step: moves + 1, occurredAt })
    assert.equal(finish(completed), completed)
    assert.equal(move(completed, 'jumpLeft', 1), completed)
    assert.equal(move(completed, 'jumpRight', 10), completed)
    assert.ok(Object.isFrozen(completed) && Object.isFrozen(completed.trace) && Object.isFrozen(completed.trace.events))
    assert.ok(completed.trace.events.every(Object.isFrozen))
    assert.throws(() => Object.assign(completed.trace.events[0], { step: 100 }), TypeError)
    const reset = sequenceAttemptReducer(completed, { kind: 'reset' })
    assert.deepEqual(reset, emptySequenceAttempt(28))
    assert.equal(move(reset, 'jumpRight', 1).trace.events[0].step, 1)
    assert.equal(completed.trace.events.length, moves + 1)
  })
}

test('reset also clears an unfinished exploration', () => {
  const state = move(emptySequenceAttempt(12), 'jumpRight', 10)
  assert.deepEqual(sequenceAttemptReducer(state, { kind: 'reset' }), emptySequenceAttempt(12))
})

test('rejects malformed or unsupported trusted fixtures and invalid starting positions', () => {
  for (const value of [null, {}, { ...descendingExercise, visualModel: 'BLOCKS' },
    { ...descendingExercise, operands: { terms: [30] } },
    { ...descendingExercise, operands: { terms: [30, null] } },
    { ...descendingExercise, operands: { terms: [30, 1.5] } },
    { ...descendingExercise, operands: { terms: [30, 2147483648] } },
    { ...descendingExercise, operands: { terms: Array(21).fill(1) } }]) {
    assert.throws(() => toSequenceExercise(value), TypeError)
  }
  for (const value of [NaN, Infinity, 1.5, 2147483648, -2147483649]) assert.throws(() => emptySequenceAttempt(value), RangeError)
})
