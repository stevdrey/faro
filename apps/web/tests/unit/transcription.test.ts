// SPDX-License-Identifier: AGPL-3.0-or-later
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { AudioError } from '../../src/features/recording/normalize.ts'
import { readTranscription, TranscriptionController } from '../../src/features/recording/transcription.ts'
import type { Transcription } from '../../src/features/recording/transcription.ts'

const result: Transcription = { schemaVersion: 1, transcript: 'seis', languageTag: 'es', engineId: 'synthetic',
  modelId: 'test', modelVersion: '1', latencyMs: 1, status: 'TRANSCRIBED' }
const blob = new Blob(['synthetic'])
function pending<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(done => { resolve = done })
  return { promise, resolve }
}
function fixture(normalize = async () => new ArrayBuffer(2), send = async (_pcm: ArrayBuffer, _signal: AbortSignal) => result) {
  let timeout = () => {}
  let cleared = 0
  const controller = new TranscriptionController({ normalize, send, schedule: callback => {
    timeout = callback
    return () => { cleared++ }
  } })
  return { controller, timeout: () => timeout(), cleared: () => cleared }
}

test('validates the technical result rather than trusting malformed provider JSON', () => {
  assert.equal(readTranscription(result).transcript, 'seis')
  assert.ok(Object.isFrozen(readTranscription(result)))
  for (const value of [null, {}, { ...result, schemaVersion: 2 }, { ...result, status: 'CORRECT' },
    { ...result, latencyMs: -1 }, { ...result, latencyMs: NaN }, { ...result, transcript: '' },
    { ...result, status: 'EMPTY' }, { ...result, engineId: null }]) {
    assert.throws(() => readTranscription(value), /provider/)
  }
})
test('normalizes before sending, emits a result, and releases its timeout', async () => {
  const { controller, cleared } = fixture()
  await controller.start(blob)
  assert.deepEqual(controller.state, { busy: false, result })
  assert.ok(cleared() > 0)
  controller.cancel()
  assert.deepEqual(controller.state, { busy: false })
})
test('cancellation while decoding prevents upload and discards late decoder completion', async () => {
  const decoded = pending<ArrayBuffer>()
  let calls = 0
  const { controller } = fixture(() => decoded.promise, async () => { calls++; return result })
  const work = controller.start(blob)
  controller.cancel()
  decoded.resolve(new ArrayBuffer(2))
  await work
  assert.equal(calls, 0)
  assert.deepEqual(controller.state, { busy: false })
})
test('cancellation aborts HTTP; late old results cannot replace the next clip result', async () => {
  const response = pending<Transcription>()
  let signal!: AbortSignal
  let calls = 0
  const { controller } = fixture(undefined, async (_pcm, nextSignal) => {
    calls++
    signal = nextSignal
    return calls === 1 ? response.promise : { ...result, transcript: 'doce' }
  })
  const old = controller.start(blob)
  await Promise.resolve()
  await controller.start(blob)
  assert.equal(calls, 1)
  controller.cancel()
  assert.ok(signal.aborted)
  await controller.start(blob)
  response.resolve(result)
  await old
  assert.equal(controller.state.result?.transcript, 'doce')
})
test('deadline invalidates pending work and failures expose only stable codes', async () => {
  const decoded = pending<ArrayBuffer>()
  const { controller, timeout } = fixture(() => decoded.promise)
  const work = controller.start(blob)
  timeout()
  decoded.resolve(new ArrayBuffer(2))
  await work
  assert.deepEqual(controller.state, { busy: false, error: 'timeout' })
  for (const failure of [new AudioError('unsupported'), new Error('synthetic private vendor data')]) {
    const { controller: failed } = fixture(async () => { throw failure })
    await failed.start(blob)
    assert.deepEqual(failed.state, { busy: false, error: failure instanceof AudioError ? 'unsupported' : 'provider' })
  }
})
