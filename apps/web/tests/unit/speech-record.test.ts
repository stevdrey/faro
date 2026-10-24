// SPDX-License-Identifier: AGPL-3.0-or-later
import assert from 'node:assert/strict'
import test from 'node:test'
import { captureRecord, transcriptionRecord } from '../../src/features/recording/speech-record.ts'
import type { CaptureError } from '../../src/features/recording/capture.ts'
import type { Transcription } from '../../src/features/recording/transcription.ts'

test('capture records distinguish empty media and permission errors and never retain the clip', () => {
  for (const error of ['permission', 'empty', 'device', 'unsupported', 'capture', 'interrupted'] satisfies CaptureError[]) {
    assert.deepEqual(captureRecord('attempt', { phase: 'error', error, elapsedMs: 0, clip: null }),
      { attemptId: 'attempt', stage: 'capture', status: 'error', error })
  }
  const clip = { blob: new Blob(['synthetic']), durationMs: 1200, mimeType: 'audio/webm' }
  const record = captureRecord('attempt', { phase: 'ready', clip, elapsedMs: 1200 })!
  assert.deepEqual(record, { attemptId: 'attempt', stage: 'capture', status: 'ready', recordingDurationMs: 1200 })
  assert.ok(Object.isFrozen(record))
  assert.equal(captureRecord('attempt', { phase: 'recording', clip: null, elapsedMs: 1000 }), null)
  assert.equal(captureRecord('attempt', { phase: 'available', clip: null, elapsedMs: 0 }), null)
})

test('ASR records preserve unconfirmed text exactly, provenance, both durations and only allowlisted properties', () => {
  for (const status of ['TRANSCRIBED', 'EMPTY', 'UNRELIABLE', 'UNAVAILABLE'] as const) {
    const result: Transcription = { schemaVersion: 1, status, transcript: status === 'TRANSCRIBED' ? ' seis ' : '',
      languageTag: 'es', engineId: 'synthetic', modelId: 'test', modelVersion: '1', latencyMs: 12, audioDurationMs: 1000 }
    const record = transcriptionRecord('attempt', 1200, { busy: false,
      result: { ...result, audio: new Blob(['synthetic']), exception: new Error('private') } as Transcription })!
    assert.deepEqual(record, { attemptId: 'attempt', stage: 'asr', status: 'result', recordingDurationMs: 1200, hypothesis: result })
    assert.ok(Object.isFrozen(record))
    if (record.stage === 'asr' && record.status === 'result') {
      assert.ok(Object.isFrozen(record.hypothesis))
      assert.throws(() => Object.assign(record.hypothesis, { transcript: 'other' }), TypeError)
    }
  }
  assert.deepEqual(transcriptionRecord('attempt', 1200, { busy: false, error: 'unsupported' }),
    { attemptId: 'attempt', stage: 'asr', status: 'error', recordingDurationMs: 1200, error: 'unsupported' })
  assert.deepEqual(transcriptionRecord('attempt', 1200, { busy: false, error: 'timeout', audioDurationMs: 1000 }),
    { attemptId: 'attempt', stage: 'asr', status: 'error', recordingDurationMs: 1200, error: 'timeout', audioDurationMs: 1000 })
  assert.equal(transcriptionRecord('attempt', 1200, { busy: true }), null)
  assert.equal(transcriptionRecord('attempt', 1200, { busy: false }), null)
})
