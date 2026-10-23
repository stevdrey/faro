// SPDX-License-Identifier: AGPL-3.0-or-later
import assert from 'node:assert/strict'
import test from 'node:test'
import { completionReducer, emptyCompletion } from '../../src/features/shared/attempt-completion.ts'
import type { CompletedTrace, CompletionState } from '../../src/features/shared/attempt-completion.ts'
import { attemptReducer, emptyAttempt } from '../../src/features/missing-addend/attempt.ts'
import { emptySequenceAttempt, sequenceAttemptReducer } from '../../src/features/sequences/attempt.ts'
import { emptySubtractionAttempt, subtractionAttemptReducer } from '../../src/features/subtraction/attempt.ts'

const occurredAt = '2026-10-22T12:00:00.000Z'
const finish = { kind: 'finishAttempt', occurredAt } as const
const traces = [
  ['blocks', attemptReducer(attemptReducer(emptyAttempt(), { kind: 'placeBlock', occurredAt }), finish).trace],
  ['sequence', sequenceAttemptReducer(sequenceAttemptReducer(emptySequenceAttempt(28), { kind: 'jumpLeft', value: 2, occurredAt }), finish).trace],
  ['subtraction', subtractionAttemptReducer(subtractionAttemptReducer(emptySubtractionAttempt(53), { kind: 'decomposeTen', occurredAt }), finish).trace],
] as const
const complete = (state: CompletionState, trace: CompletedTrace, exerciseId = state.exerciseId) =>
  completionReducer(state, { kind: 'complete', exerciseId, trace })

for (const [exerciseId, trace] of traces) {
  test(`${exerciseId}: retains the same frozen trace and associates it with the selected exercise`, () => {
    const initial = emptyCompletion(exerciseId)
    const state = complete(initial, trace)
    assert.deepEqual(initial, { exerciseId, completedAttempt: null })
    assert.equal(state.completedAttempt?.exerciseId, exerciseId)
    assert.equal(state.completedAttempt?.trace, trace)
    assert.ok(Object.isFrozen(state) && Object.isFrozen(state.completedAttempt))
    assert.ok(Object.isFrozen(trace) && Object.isFrozen(trace.events) && trace.events.every(Object.isFrozen))
    assert.throws(() => Object.assign(state.completedAttempt!, { exerciseId: 'other' }), TypeError)
    assert.throws(() => Object.assign(trace.events[0], { step: 99 }), TypeError)
    assert.equal(trace.schemaVersion, exerciseId === 'subtraction' ? 2 : 1)
  })

  test(`${exerciseId}: repeated delivery or another completion cannot overwrite the first snapshot`, () => {
    const state = complete(emptyCompletion(exerciseId), trace)
    assert.equal(complete(state, trace), state)
    assert.equal(complete(state, traces[0][1]), state)
    assert.equal(completionReducer(state, { kind: 'selectExercise', exerciseId }), state)
  })

  test(`${exerciseId}: resetting or switching releases the snapshot without changing retained evidence`, () => {
    const completed = complete(emptyCompletion(exerciseId), trace)
    const evidence = JSON.stringify(completed.completedAttempt)
    const reset = completionReducer(completed, { kind: 'reset', exerciseId })
    assert.deepEqual(reset, emptyCompletion(exerciseId))
    assert.equal(complete(reset, trace).completedAttempt?.trace, trace)
    const switched = completionReducer(completed, { kind: 'selectExercise', exerciseId: 'other' })
    assert.deepEqual(switched, emptyCompletion('other'))
    assert.deepEqual(completionReducer(switched, { kind: 'selectExercise', exerciseId }), emptyCompletion(exerciseId))
    assert.equal(JSON.stringify(completed.completedAttempt), evidence)
  })
}

test('exploration and callbacks from another exercise cannot complete or reset the selected attempt', () => {
  const initial = emptyCompletion('blocks')
  assert.equal(complete(initial, emptyAttempt().trace), initial)
  const exploring = attemptReducer(emptyAttempt(), { kind: 'placeBlock', occurredAt }).trace
  assert.equal(complete(initial, exploring), initial)
  assert.equal(complete(initial, traces[0][1], 'other'), initial)
  assert.equal(completionReducer(initial, { kind: 'reset', exerciseId: 'blocks' }), initial)
  const completed = complete(initial, traces[0][1])
  assert.equal(completionReducer(completed, { kind: 'reset', exerciseId: 'other' }), completed)
  assert.equal(complete(emptyCompletion('blocks'), traces[0][1]).completedAttempt?.exerciseId, 'blocks')
})
