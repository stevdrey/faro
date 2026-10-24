// SPDX-License-Identifier: AGPL-3.0-or-later
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { ASR_SAMPLE_RATE, AudioError, encodePcm, MAX_ENCODED_BYTES, MAX_PCM_BYTES, normalizeClip, OVERRUN_TOLERANCE_MS } from '../../src/features/recording/normalize.ts'

test('encodes mono signed PCM16 little endian with saturation and preserves silence', () => {
  const pcm = encodePcm([new Float32Array([-2, -1, -.5, 0, .5, 1, 2])])
  const view = new DataView(pcm)
  assert.deepEqual(Array.from({ length: 7 }, (_, index) => view.getInt16(index * 2, true)), [-32768, -32768, -16384, 0, 16384, 32767, 32767])
  assert.deepEqual([...new Uint8Array(pcm).slice(0, 2)], [0, 128])
})
test('downmixes stereo to mono without changing duration or clipping opposite channels', () => {
  assert.deepEqual([...new Uint8Array(encodePcm([new Float32Array([1, -.5]), new Float32Array([-1, .5])]))], [0, 0, 0, 0])
})
test('downmixes any channel count, such as multi-channel input devices', () => {
  const channels = [new Float32Array([.3]), new Float32Array([.3]), new Float32Array([.3]), new Float32Array([.3])]
  assert.equal(new DataView(encodePcm(channels)).getInt16(0, true), Math.round(.3 * 32767))
})
test('rejects invalid channels and samples; trims the auto-stop overrun to 30 seconds and rejects longer audio', () => {
  for (const channels of [[], [new Float32Array()], [new Float32Array([NaN])], [new Float32Array([Infinity])],
    [new Float32Array(1), new Float32Array(2)]]) {
    assert.throws(() => encodePcm(channels), (error: unknown) => error instanceof AudioError && error.code === 'invalid')
  }
  assert.equal(encodePcm([new Float32Array(MAX_PCM_BYTES / 2)]).byteLength, MAX_PCM_BYTES)
  // A 30 s auto-stop typically decodes a few tens of milliseconds longer.
  assert.equal(encodePcm([new Float32Array(MAX_PCM_BYTES / 2 + ASR_SAMPLE_RATE * .02)]).byteLength, MAX_PCM_BYTES)
  const tolerated = MAX_PCM_BYTES / 2 + ASR_SAMPLE_RATE * OVERRUN_TOLERANCE_MS / 1000
  assert.equal(encodePcm([new Float32Array(tolerated)]).byteLength, MAX_PCM_BYTES)
  assert.throws(() => encodePcm([new Float32Array(tolerated + 1)]), /limit/)
})
test('rejects empty/oversized encoded files before invoking a browser decoder', async () => {
  await assert.rejects(normalizeClip(new Blob()), /invalid/)
  await assert.rejects(normalizeClip(new Blob([new Uint8Array(MAX_ENCODED_BYTES + 1)])), /limit/)
  await assert.rejects(normalizeClip(new Blob(['synthetic'])), /unsupported/)
})
