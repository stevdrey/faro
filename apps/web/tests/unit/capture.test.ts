// SPDX-License-Identifier: AGPL-3.0-or-later
import assert from 'node:assert/strict'
import test from 'node:test'
import { CaptureController, MAX_RECORDING_MS } from '../../src/features/recording/capture.ts'
import { AudioCoordinator } from '../../src/features/recording/audio-coordinator.ts'

class Track extends EventTarget {
  readyState = 'live'
  stops = 0
  stop() { this.stops++; this.readyState = 'ended' }
}
class Recorder extends EventTarget {
  state = 'inactive'
  mimeType = 'audio/synthetic'
  starts = 0
  stops = 0
  throwStart = false
  throwStop = false
  start() {
    if (this.throwStart) throw new Error('Private vendor details')
    this.starts++
    this.state = 'recording'
  }
  stop() {
    if (this.throwStop) throw new Error('Private vendor details')
    this.stops++
    this.state = 'inactive'
  }
  data(text = 'synthetic') {
    this.dispatchEvent(Object.assign(new Event('dataavailable'), { data: new Blob([text], { type: this.mimeType }) }))
  }
  finish(text = 'synthetic') { this.data(text); this.dispatchEvent(new Event('stop')) }
}
function setup() {
  const tracks = [new Track(), new Track()]
  const stream = { getTracks: () => tracks, getAudioTracks: () => tracks } as unknown as MediaStream
  const recorder = new Recorder()
  let now = 0
  let requests = 0
  const timers = new Set<{ callback: () => void; at: number }>()
  let getStream = () => Promise.resolve(stream)
  let makeRecorder = () => recorder as unknown as MediaRecorder
  const controller = new CaptureController({
    getStream: () => { requests++; return getStream() },
    makeRecorder: () => makeRecorder(),
    now: () => now,
    schedule: (callback, delay) => {
      const timer = { callback, at: now + delay }
      timers.add(timer)
      return () => { timers.delete(timer) }
    },
  })
  return {
    controller, tracks, recorder, timers, stream, requests: () => requests,
    setStream: (next: typeof getStream) => { getStream = next },
    setRecorder: (next: typeof makeRecorder) => { makeRecorder = next },
    advance: (time: number) => {
      now += time
      for (const timer of [...timers]) if (timer.at <= now && timers.delete(timer)) timer.callback()
    },
    released: () => { assert.ok(tracks.every(track => track.readyState === 'ended')); assert.equal(timers.size, 0) },
  }
}

test('capture is explicit, blocks repeated starts, finalizes all chunks and stops every track', async () => {
  const f = setup()
  const phases: string[] = []
  const unsubscribe = f.controller.subscribe(state => phases.push(state.phase))
  assert.equal(f.requests(), 0)
  const start = f.controller.start()
  await f.controller.start()
  await start
  await f.controller.start()
  assert.equal(f.requests(), 1)
  assert.equal(f.recorder.starts, 1)
  f.recorder.data('first')
  f.advance(1250)
  f.controller.stop()
  f.controller.stop()
  assert.equal(f.controller.state.phase, 'finalizing')
  assert.equal(f.controller.state.clip, null)
  f.released()
  assert.equal(f.recorder.stops, 1)
  f.recorder.finish('last')
  assert.equal(await f.controller.state.clip!.blob.text(), 'firstlast')
  assert.equal(f.controller.state.clip!.durationMs, 1250)
  assert.equal(f.controller.state.clip!.mimeType, 'audio/synthetic')
  assert.deepEqual(phases, ['requesting', 'recording', 'recording', 'finalizing', 'ready'])
  unsubscribe()
  f.controller.cancel()
  assert.equal(f.controller.state.clip, null)
  assert.equal(phases.at(-1), 'ready')
})

test('the duration limit automatically stops, and elapsed time is bounded', async () => {
  const f = setup()
  await f.controller.start()
  f.advance(MAX_RECORDING_MS + 150)
  assert.equal(f.controller.state.phase, 'finalizing')
  f.recorder.finish()
  assert.equal(f.controller.state.clip!.durationMs, MAX_RECORDING_MS)
  f.released()
})

for (const phase of ['recording', 'finalizing', 'ready'] as const) {
  test(`cancel during ${phase} discards chunks, removes listeners and rejects queued events`, async () => {
    const f = setup()
    await f.controller.start()
    f.recorder.data()
    if (phase !== 'recording') f.controller.stop()
    if (phase === 'ready') f.recorder.finish()
    f.controller.cancel()
    f.recorder.finish()
    f.recorder.dispatchEvent(new Event('error'))
    f.tracks[0].dispatchEvent(new Event('ended'))
    assert.equal(f.controller.state.phase, 'available')
    assert.equal(f.controller.state.clip, null)
    f.released()
  })
}

test('a permission granted after cancellation releases its stream without creating a recorder', async () => {
  const f = setup()
  let resolve!: (stream: MediaStream) => void
  f.setStream(() => new Promise(value => { resolve = value }))
  const pending = f.controller.start()
  f.controller.cancel()
  resolve(f.stream)
  await pending
  assert.equal(f.controller.state.phase, 'available')
  assert.equal(f.recorder.starts, 0)
  f.released()
})

test('an old permission rejection cannot overwrite a newer recording', async () => {
  const f = setup()
  let reject!: (error: unknown) => void
  f.setStream(() => new Promise((_, fail) => { reject = fail }))
  const old = f.controller.start()
  f.controller.cancel()
  f.setStream(() => Promise.resolve(f.stream))
  await f.controller.start()
  reject(new DOMException('Private message', 'NotAllowedError'))
  await old
  assert.equal(f.controller.state.phase, 'recording')
  f.controller.cancel()
  f.released()
})

for (const [name, code] of [
  ['NotAllowedError', 'permission'], ['SecurityError', 'permission'], ['NotFoundError', 'device'],
  ['NotReadableError', 'device'], ['NotSupportedError', 'unsupported'], ['UnknownError', 'capture'],
]) {
  test(`${name} becomes a bounded error code without private exception text`, async () => {
    const f = setup()
    f.setStream(() => Promise.reject(new DOMException('Private identifying text', name)))
    await f.controller.start()
    assert.deepEqual(f.controller.state, { phase: 'error', elapsedMs: 0, clip: null, error: code })
    f.setStream(() => Promise.resolve(f.stream))
    await f.controller.start()
    assert.equal(f.controller.state.phase, 'recording')
    f.controller.cancel()
    f.released()
  })
}

for (const failure of ['construction', 'start', 'stop', 'error', 'ended', 'mute', 'unexpected-stop', 'empty', 'dead-stream']) {
  test(`${failure} never retains a clip and releases capture resources`, async () => {
    const f = setup()
    if (failure === 'construction') f.setRecorder(() => { throw new Error('Private details') })
    if (failure === 'start') f.recorder.throwStart = true
    if (failure === 'stop') f.recorder.throwStop = true
    if (failure === 'dead-stream') for (const track of f.tracks) track.readyState = 'ended'
    await f.controller.start()
    if (failure === 'stop' || failure === 'empty') f.controller.stop()
    if (failure === 'empty') f.recorder.finish('')
    if (failure === 'error') f.recorder.dispatchEvent(new Event('error'))
    if (failure === 'ended' || failure === 'mute') f.tracks[0].dispatchEvent(new Event(failure))
    if (failure === 'unexpected-stop') f.recorder.dispatchEvent(new Event('stop'))
    assert.equal(f.controller.state.phase, 'error')
    assert.equal(f.controller.state.clip, null)
    f.released()
    const state = f.controller.state
    f.recorder.finish()
    assert.equal(f.controller.state, state)
  })
}

test('a controller is reusable after cleanup, as required by React StrictMode', async () => {
  const f = setup()
  f.controller.cancel()
  await f.controller.start()
  assert.equal(f.controller.state.phase, 'recording')
  f.controller.cancel()
  f.released()
})

test('audio leases serialize playback, exclude it throughout permission/finalization and ignore stale releases', () => {
  const audio = new AudioCoordinator()
  let stopped = 0
  const oldRelease = audio.play(() => { stopped++ })!
  const newRelease = audio.play(() => { stopped++ })!
  assert.equal(stopped, 1)
  oldRelease()
  let notifications = 0
  const unsubscribe = audio.subscribe(() => { notifications++ })
  const releaseCapture = audio.record()!
  assert.equal(stopped, 2)
  assert.equal(audio.isCapturing(), true)
  newRelease()
  assert.equal(audio.play(() => {}), null)
  assert.equal(audio.record(), null)
  releaseCapture()
  const nextCapture = audio.record()!
  releaseCapture()
  assert.equal(audio.isCapturing(), true)
  nextCapture()
  unsubscribe()
  assert.equal(notifications, 4)
  assert.equal(audio.isCapturing(), false)
})
