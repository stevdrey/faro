// SPDX-License-Identifier: AGPL-3.0-or-later
import assert from 'node:assert/strict'
import test from 'node:test'
import { completionReducer, emptyCompletion, newAttemptId } from '../../src/features/shared/attempt-completion.ts'
import type { CompletedTrace, CompletionState } from '../../src/features/shared/attempt-completion.ts'
import { attemptReducer, emptyAttempt } from '../../src/features/missing-addend/attempt.ts'
import { emptySequenceAttempt, sequenceAttemptReducer } from '../../src/features/sequences/attempt.ts'
import { emptySubtractionAttempt, subtractionAttemptReducer } from '../../src/features/subtraction/attempt.ts'
import { captureRecord } from '../../src/features/recording/speech-record.ts'

const occurredAt = '2026-10-22T12:00:00.000Z'
const finish = { kind: 'finishAttempt', occurredAt } as const
const traces = [
  ['blocks', attemptReducer(attemptReducer(emptyAttempt(), { kind: 'placeBlock', occurredAt }), finish).trace],
  ['sequence', sequenceAttemptReducer(sequenceAttemptReducer(emptySequenceAttempt(28), { kind: 'jumpLeft', value: 2, occurredAt }), finish).trace],
  ['subtraction', subtractionAttemptReducer(subtractionAttemptReducer(emptySubtractionAttempt(53), { kind: 'decomposeTen', occurredAt }), finish).trace],
] as const
const complete = (state: CompletionState, trace: CompletedTrace, exerciseId = state.exerciseId, attemptId = 'attempt-1') =>
  completionReducer(state, { kind: 'complete', attemptId, exerciseId, trace })

for (const [exerciseId, trace] of traces) {
  test(`${exerciseId}: retains the same frozen trace and associates it with the selected exercise`, () => {
    const initial = emptyCompletion(exerciseId)
    const state = complete(initial, trace)
    assert.deepEqual(initial, { exerciseId, completedAttempt: null, speechRecord: null })
    assert.equal(state.completedAttempt?.attemptId, 'attempt-1')
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

test('speech replaces one current record without changing the snapshot and stale attempts cannot write or clear it', () => {
  const completed = complete(emptyCompletion('blocks'), traces[0][1])
  const record = captureRecord('attempt-1', { phase: 'error', error: 'permission', elapsedMs: 0, clip: null })!
  const update = { kind: 'speech', attemptId: 'attempt-1', record } as const
  assert.equal(completionReducer(emptyCompletion('blocks'), update).speechRecord, null)
  const withSpeech = completionReducer(completed, update)
  assert.equal(withSpeech.completedAttempt, completed.completedAttempt)
  assert.equal(withSpeech.speechRecord, record)
  assert.ok(Object.isFrozen(withSpeech))
  const replacement = captureRecord('attempt-1', { phase: 'error', error: 'empty', elapsedMs: 0, clip: null })!
  assert.equal(completionReducer(withSpeech, { ...update, record: replacement }).speechRecord, replacement)
  assert.equal(completionReducer(withSpeech, { kind: 'selectExercise', exerciseId: 'blocks' }), withSpeech)
  assert.equal(completionReducer(withSpeech, { ...update, attemptId: 'other' }), withSpeech)
  assert.equal(completionReducer(withSpeech, { ...update, record: { ...record, attemptId: 'other' } }), withSpeech)
  assert.equal(completionReducer(withSpeech, { ...update, record: null }).speechRecord, null)
  for (const cleared of [
    completionReducer(withSpeech, { kind: 'reset', exerciseId: 'blocks' }),
    completionReducer(withSpeech, { kind: 'selectExercise', exerciseId: 'other' }),
  ]) {
    assert.equal(cleared.speechRecord, null)
    assert.equal(completionReducer(cleared, update), cleared)
  }
  const next = complete(completionReducer(withSpeech, { kind: 'reset', exerciseId: 'blocks' }), traces[0][1], 'blocks', 'attempt-2')
  assert.equal(next.completedAttempt?.attemptId, 'attempt-2')
  assert.equal(completionReducer(next, update), next)
  assert.equal(completionReducer(next, { ...update, record: null }), next)
  assert.equal(complete(withSpeech, traces[0][1], 'blocks', 'ignored-id'), withSpeech)
})

test('attempt ids are random 128-bit values that do not require a secure context', () => {
  const descriptor = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(crypto), 'randomUUID')!
  Object.defineProperty(Object.getPrototypeOf(crypto), 'randomUUID', { configurable: true, value: undefined })
  try {
    const ids = Array.from({ length: 64 }, newAttemptId)
    assert.ok(ids.every(id => /^[0-9a-f]{32}$/.test(id)))
    assert.equal(new Set(ids).size, ids.length)
  } finally { Object.defineProperty(Object.getPrototypeOf(crypto), 'randomUUID', descriptor) }
})
