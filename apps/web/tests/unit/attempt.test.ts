// SPDX-License-Identifier: AGPL-3.0-or-later
import assert from 'node:assert/strict'
import test from 'node:test'
import { attemptReducer, emptyAttempt } from '../../src/features/missing-addend/attempt.ts'
import type { AttemptState } from '../../src/features/missing-addend/attempt.ts'

const occurredAt = '2026-01-01T00:00:00.000Z'
const add = (state: AttemptState) => attemptReducer(state, { kind: 'placeBlock', occurredAt })
const remove = (state: AttemptState) => attemptReducer(state, { kind: 'removeBlock', occurredAt })
const finish = (state: AttemptState) => attemptReducer(state, { kind: 'finishAttempt', occurredAt })

test('starts empty; keeps every reachable quantity in 0–10 and records only accepted moves', () => {
  let state = emptyAttempt()
  assert.deepEqual(state.trace, { schemaVersion: 1, initialValue: 0, events: [] })
  assert.equal(remove(state), state)
  for (let count = 1; count <= 10; count++) {
    state = add(state)
    assert.equal(state.count, count)
    assert.equal(state.completed, false)
  }
  assert.equal(add(state), state)
  assert.equal(state.trace.events.length, 10)
  for (let count = 9; count >= 0; count--) {
    state = remove(state)
    assert.equal(state.count, count)
  }
  assert.equal(remove(state), state)
  assert.equal(state.trace.events.length, 20)
})

test('corrections preserve ordered semantic evidence without mutating earlier states', () => {
  const initial = emptyAttempt()
  const first = add(initial)
  const corrected = add(remove(add(first)))
  assert.equal(corrected.count, 2)
  assert.deepEqual(corrected.trace.events, [
    { kind: 'placeBlock', value: 1, step: 1, occurredAt },
    { kind: 'placeBlock', value: 1, step: 2, occurredAt },
    { kind: 'removeBlock', value: 1, step: 3, occurredAt },
    { kind: 'placeBlock', value: 1, step: 4, occurredAt },
  ])
  assert.equal(initial.trace.events.length, 0)
  assert.equal(first.trace.events.length, 1)
})

for (const count of [0, 6, 7, 10]) {
  test(`finishes neutrally at ${count}, freezes the snapshot, and rejects later movement/finish`, () => {
    let state = emptyAttempt()
    for (let index = 0; index < count; index++) state = add(state)
    const completed = finish(state)
    assert.equal(completed.count, count)
    assert.equal(completed.completed, true)
    assert.deepEqual(completed.trace.events.at(-1), { kind: 'finishAttempt', step: count + 1, occurredAt })
    assert.equal(finish(completed), completed)
    assert.equal(add(completed), completed)
    assert.equal(remove(completed), completed)
    assert.ok(Object.isFrozen(completed))
    assert.ok(Object.isFrozen(completed.trace))
    assert.ok(Object.isFrozen(completed.trace.events))
    assert.ok(completed.trace.events.every(Object.isFrozen))
    assert.throws(() => Object.assign(completed.trace.events[0], { kind: 'removeBlock' }), TypeError)
  })
}

test('reset forgets the completed attempt and restarts step numbering at one', () => {
  const completed = finish(add(emptyAttempt()))
  const reset = attemptReducer(completed, { kind: 'reset' })
  assert.deepEqual(reset, emptyAttempt())
  assert.notEqual(reset.trace, completed.trace)
  assert.equal(add(reset).trace.events[0].step, 1)
  assert.equal(completed.trace.events.length, 2)
})

test('timestamps are supplied evidence; steps still define ordering after a clock adjustment', () => {
  const first = add(emptyAttempt())
  const earlier = '2025-12-31T23:59:59.000Z'
  const corrected = attemptReducer(first, { kind: 'removeBlock', occurredAt: earlier })
  assert.deepEqual(corrected.trace.events.map(event => event.step), [1, 2])
  assert.equal(corrected.trace.events[1].occurredAt, earlier)
})
