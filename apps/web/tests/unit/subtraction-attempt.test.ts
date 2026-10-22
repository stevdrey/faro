// SPDX-License-Identifier: AGPL-3.0-or-later
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import type { ActionEventV2, ActionTracePayloadV2 } from '../../src/contracts/action-v2.ts'
import { canOperate, emptySubtractionAttempt, replayTensAndOnes, subtractionAttemptReducer } from '../../src/features/subtraction/attempt.ts'
import type { Operation, SubtractionAttempt } from '../../src/features/subtraction/attempt.ts'
import { toSubtractionExercise } from '../../src/features/subtraction/fixture.ts'

const at = '2026-01-01T00:00:00Z'
const fixture = JSON.parse(readFileSync(new URL('../../../../contracts/actions/v2/fixtures/subtraction.json', import.meta.url), 'utf8')) as ActionTracePayloadV2
const exercise: unknown = JSON.parse(readFileSync(new URL('../../../../contracts/exercises/v1/fixtures/subtraction.json', import.meta.url), 'utf8'))
const move = (state: SubtractionAttempt, operation: Operation) => subtractionAttemptReducer(state, { ...operation, occurredAt: at })
const totals = (state: SubtractionAttempt) => [state.tens * 10 + state.ones, state.removedTens * 10 + state.removedOnes]

test('shared fixture replays every exchange and correction to the Java result', () => {
  assert.deepEqual(toSubtractionExercise(exercise), exercise)
  let state = emptySubtractionAttempt(fixture.initialValue)
  for (const event of fixture.events) {
    state = subtractionAttemptReducer(state, event as Operation & { occurredAt: string })
    assert.deepEqual(replayTensAndOnes(state.trace), {
      tens: state.tens, ones: state.ones, removedTens: state.removedTens, removedOnes: state.removedOnes, completed: state.completed,
    })
    assert.equal(totals(state).reduce((a, b) => a + b), 53)
  }
  assert.deepEqual(state.trace, fixture)
  assert.deepEqual(replayTensAndOnes(fixture), { tens: 2, ones: 5, removedTens: 2, removedOnes: 8, completed: true })
  assert.deepEqual(replayTensAndOnes(fixture), replayTensAndOnes(fixture))
})

test('removes twenty, exchanges manually and removes eight without computing an answer', () => {
  const initial = emptySubtractionAttempt(53)
  let state = move(move(initial, { kind: 'removeBlock', value: 10 }), { kind: 'removeBlock', value: 10 })
  assert.deepEqual(totals(state), [33, 20])
  state = move(state, { kind: 'decomposeTen' })
  assert.equal(state.tens, 2)
  assert.equal(state.ones, 13)
  assert.deepEqual(totals(state), [33, 20])
  for (let i = 0; i < 8; i++) state = move(state, { kind: 'removeBlock', value: 1 })
  assert.deepEqual(totals(state), [25, 28])
  assert.equal(state.completed, false)
  assert.deepEqual(totals(initial), [53, 0])
})

test('does not exchange automatically, permits removing everything and returning it', () => {
  let state = emptySubtractionAttempt(53)
  for (let i = 0; i < 3; i++) state = move(state, { kind: 'removeBlock', value: 1 })
  assert.equal(move(state, { kind: 'removeBlock', value: 1 }), state)
  assert.equal(state.tens, 5)
  for (let i = 0; i < 5; i++) state = move(state, { kind: 'removeBlock', value: 10 })
  assert.deepEqual(totals(state), [0, 53])
  assert.equal(move(state, { kind: 'decomposeTen' }), state)
  for (let i = 0; i < 5; i++) state = move(state, { kind: 'placeBlock', value: 10 })
  for (let i = 0; i < 3; i++) state = move(state, { kind: 'placeBlock', value: 1 })
  assert.deepEqual(totals(state), [53, 0])
  assert.equal(move(state, { kind: 'placeBlock', value: 1 }), state)
  assert.equal(move(state, { kind: 'placeBlock', value: 10 }), state)
})

test('composing uses available units only, without touching removed pieces', () => {
  const initial = emptySubtractionAttempt(53)
  assert.equal(move(initial, { kind: 'composeTen' }), initial)
  const decomposed = move(initial, { kind: 'decomposeTen' })
  assert.deepEqual(totals(move(decomposed, { kind: 'composeTen' })), [53, 0])
  let state = decomposed
  for (let i = 0; i < 4; i++) state = move(state, { kind: 'removeBlock', value: 1 })
  assert.equal(state.ones, 9)
  assert.equal(state.removedOnes, 4)
  assert.equal(move(state, { kind: 'composeTen' }), state)
})

for (const initial of [0, 53, 2147483647]) {
  test(`finishes once, freezes and resets at initial quantity ${initial}`, () => {
    const state = emptySubtractionAttempt(initial)
    const completed = move(state, { kind: 'finishAttempt' })
    for (const operation of [{ kind: 'decomposeTen' }, { kind: 'composeTen' }, { kind: 'removeBlock', value: 1 }, { kind: 'finishAttempt' }] as const) {
      assert.equal(move(completed, operation), completed)
    }
    assert.ok(Object.isFrozen(completed) && Object.isFrozen(completed.trace) && Object.isFrozen(completed.trace.events) && completed.trace.events.every(Object.isFrozen))
    assert.deepEqual(subtractionAttemptReducer(completed, { kind: 'reset' }), state)
    assert.deepEqual(replayTensAndOnes(completed.trace), { tens: state.tens, ones: state.ones, removedTens: 0, removedOnes: 0, completed: true })
  })
}

test('exploration preserves conservation, previous snapshots and replay agreement', () => {
  let state = emptySubtractionAttempt(53)
  const operations: Operation[] = [
    { kind: 'removeBlock', value: 10 }, { kind: 'removeBlock', value: 1 },
    { kind: 'placeBlock', value: 10 }, { kind: 'placeBlock', value: 1 },
    { kind: 'decomposeTen' }, { kind: 'composeTen' },
  ]
  let seed = 24
  for (let i = 0; i < 500; i++) {
    seed = (seed * 1664525 + 1013904223) >>> 0
    const operation = operations[seed % operations.length]
    const previous = JSON.stringify(state)
    const next = move(state, operation)
    assert.equal(JSON.stringify(state), previous)
    assert.equal(next !== state, canOperate(state, operation))
    assert.equal(totals(next).reduce((a, b) => a + b), 53)
    assert.deepEqual(replayTensAndOnes(next.trace), { tens: next.tens, ones: next.ones, removedTens: next.removedTens, removedOnes: next.removedOnes, completed: false })
    state = next
  }
  assert.deepEqual(subtractionAttemptReducer(state, { kind: 'reset' }), emptySubtractionAttempt(53))
})

test('steps order events even with decreasing or equal timestamps', () => {
  const trace: ActionTracePayloadV2 = { schemaVersion: 2, initialValue: 10, events: [
    { kind: 'decomposeTen', step: 1, occurredAt: '2026-01-01T00:00:01.123456789Z' },
    { kind: 'composeTen', step: 2, occurredAt: at },
  ] }
  assert.deepEqual(replayTensAndOnes(trace), { tens: 1, ones: 0, removedTens: 0, removedOnes: 0, completed: false })
})

test('replay rejects invalid operations, metadata, ordering and post-completion events', () => {
  const event = { kind: 'decomposeTen', step: 1, occurredAt: at }
  const invalid = [
    { ...event, kind: 'jumpLeft', value: 1 }, { ...event, kind: 'jumpRight', value: 1 },
    { ...event, kind: 'placeBlock', value: 10 }, { ...event, kind: 'removeBlock', value: 8 },
    { ...event, kind: 'removeBlock', value: 1.5 }, { ...event, kind: 'composeTen' },
    { ...event, value: 10 }, { ...event, step: 2 }, { ...event, occurredAt: '2026-02-30T00:00:00Z' },
    { ...event, occurredAt: '2026-01-01T24:00:00Z' }, { ...event, occurredAt: '2026-01-01T00:00:00+00:00' },
  ]
  for (const invalidEvent of invalid) {
    assert.throws(() => replayTensAndOnes({ schemaVersion: 2, initialValue: 53, events: [invalidEvent as ActionEventV2] }))
  }
  assert.throws(() => replayTensAndOnes({ schemaVersion: 2, initialValue: 0, events: [event as ActionEventV2] }))
  assert.throws(() => replayTensAndOnes({ schemaVersion: 2, initialValue: 53, events: [
    { kind: 'finishAttempt', step: 1, occurredAt: at }, { kind: 'decomposeTen', step: 2, occurredAt: at },
  ] }))
  for (const initialValue of [-1, 0.5, 2147483648, NaN]) assert.throws(() => emptySubtractionAttempt(initialValue))
})

test('fixture narrowing preserves data and rejects unsupported or oversized presentations', () => {
  const valid = toSubtractionExercise(exercise)
  for (const change of [
    { exerciseId: '' }, { schemaVersion: 2 }, { type: 'SEQUENCE' }, { visualModel: 'BLOCKS' }, { difficulty: 'TRIVIAL' },
    ...[{ minuend: -1, subtrahend: 0 }, { minuend: 100, subtrahend: 28 }, { minuend: 53, subtrahend: 54 },
      { minuend: 53, subtrahend: 0.5 }].map(operands => ({ operands })),
  ]) assert.throws(() => toSubtractionExercise({ ...valid, ...change }), TypeError)
  assert.throws(() => toSubtractionExercise(null), TypeError)
})
