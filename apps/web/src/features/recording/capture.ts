// SPDX-License-Identifier: AGPL-3.0-or-later
export const MAX_RECORDING_MS = 30_000
/** A mute shorter than this (an OS interruption or notification) does not discard the recording. */
export const MUTE_GRACE_MS = 1_500
export interface RecordedClip {
  readonly blob: Blob
  readonly mimeType: string
  readonly durationMs: number
}
export type CaptureError = 'permission' | 'device' | 'unsupported' | 'interrupted' | 'empty' | 'capture'
export interface CaptureState {
  readonly phase: 'available' | 'requesting' | 'recording' | 'finalizing' | 'ready' | 'error'
  readonly elapsedMs: number
  readonly clip: RecordedClip | null
  readonly error?: CaptureError
}
export interface CaptureEnvironment {
  getStream: () => Promise<MediaStream>
  makeRecorder: (stream: MediaStream) => MediaRecorder
  now: () => number
  schedule: (callback: () => void, delayMs: number) => () => void
}
const browserEnvironment: CaptureEnvironment = {
  getStream: () => {
    if (!globalThis.isSecureContext || !navigator.mediaDevices?.getUserMedia || !globalThis.MediaRecorder) {
      return Promise.reject(new DOMException('', 'NotSupportedError'))
    }
    return navigator.mediaDevices.getUserMedia({ audio: true, video: false })
  },
  makeRecorder: stream => new MediaRecorder(stream),
  now: () => performance.now(),
  schedule: (callback, delay) => {
    const timer = setTimeout(callback, delay)
    return () => clearTimeout(timer)
  },
}
const available = (): CaptureState => ({ phase: 'available', elapsedMs: 0, clip: null })
export const isInFlight = (phase: CaptureState['phase']) =>
  phase === 'requesting' || phase === 'recording' || phase === 'finalizing'
/** Maps only the exception name, so private vendor text never reaches the UI. */
function classify(error: unknown): CaptureError {
  const name = error instanceof DOMException ? error.name : ''
  return name === 'NotAllowedError' || name === 'SecurityError' ? 'permission'
    : name === 'NotFoundError' || name === 'NotReadableError' ? 'device'
    : name === 'NotSupportedError' ? 'unsupported' : 'capture'
}
function stopTracks(stream: MediaStream) {
  for (const track of stream.getTracks()) {
    try { track.stop() } catch { /* Release the remaining tracks too. */ }
  }
}

/** Owns only transient media. Cancel invalidates even a still-pending permission request. */
export class CaptureController {
  state: CaptureState = available()
  private generation = 0
  private listeners = new Set<(state: CaptureState) => void>()
  private cleanup: (() => void) | null = null
  private stopRecording: (() => void) | null = null
  private readonly environment: CaptureEnvironment
  constructor(environment: CaptureEnvironment = browserEnvironment) { this.environment = environment }

  subscribe(listener: (state: CaptureState) => void) {
    this.listeners.add(listener)
    return () => { this.listeners.delete(listener) }
  }
  private show(state: CaptureState) {
    this.state = Object.freeze(state)
    for (const listener of this.listeners) listener(this.state)
  }
  private release() {
    this.generation++
    this.cleanup?.()
    this.cleanup = null
    this.stopRecording = null
  }
  cancel() {
    this.release()
    this.show(available())
  }
  stop() { this.stopRecording?.() }
  async start() {
    if (isInFlight(this.state.phase)) return
    this.release()
    const generation = ++this.generation
    const current = () => generation === this.generation
    const fail = (error: CaptureError) => {
      if (!current()) return
      this.cancel()
      this.show({ phase: 'error', elapsedMs: 0, clip: null, error })
    }
    this.show({ phase: 'requesting', elapsedMs: 0, clip: null })
    let stream: MediaStream
    try {
      stream = await this.environment.getStream()
    } catch (error) {
      fail(classify(error))
      return
    }
    if (!current()) { stopTracks(stream); return }
    let recorder: MediaRecorder | undefined
    let chunks: Blob[] = []
    let durationMs = 0
    let startedAt = 0
    let cancelTick = () => {}
    let cancelLimit = () => {}
    let cancelMute = () => {}
    const muted = new Set<EventTarget>()
    const cleanups: (() => void)[] = []
    this.cleanup = () => {
      cancelTick()
      cancelLimit()
      cancelMute()
      for (const remove of cleanups) remove()
      try { if (recorder && recorder.state !== 'inactive') recorder.stop() } catch { /* Already failed. */ }
      stopTracks(stream)
      chunks = []
    }
    try {
      if (!stream.getAudioTracks().some(track => track.readyState === 'live')) { fail('device'); return }
      recorder = this.environment.makeRecorder(stream)
      const listen = (target: EventTarget, type: string, listener: EventListener) => {
        target.addEventListener(type, listener)
        cleanups.push(() => target.removeEventListener(type, listener))
      }
      for (const track of stream.getAudioTracks()) {
        listen(track, 'ended', () => { if (current() && this.state.phase === 'recording') fail('interrupted') })
        listen(track, 'mute', () => {
          if (!current() || this.state.phase !== 'recording') return
          if (!muted.size) cancelMute = this.environment.schedule(() => {
            if (current() && this.state.phase === 'recording' && muted.size) fail('interrupted')
          }, MUTE_GRACE_MS)
          muted.add(track)
        })
        listen(track, 'unmute', () => {
          muted.delete(track)
          if (!muted.size) { cancelMute(); cancelMute = () => {} }
        })
      }
      listen(recorder, 'error', () => fail('capture'))
      listen(recorder, 'dataavailable', event => {
        if (current() && (event as BlobEvent).data.size > 0) chunks.push((event as BlobEvent).data)
      })
      listen(recorder, 'stop', () => {
        if (!current()) return
        if (this.state.phase !== 'finalizing') { fail('interrupted'); return }
        const mimeType = recorder!.mimeType || chunks[0]?.type || ''
        const blob = new Blob(chunks, { type: mimeType })
        if (!blob.size) { fail('empty'); return }
        const clip = Object.freeze({ blob, mimeType, durationMs })
        this.cleanup?.()
        this.cleanup = null
        this.stopRecording = null
        this.show({ phase: 'ready', elapsedMs: durationMs, clip })
      })
      this.stopRecording = () => {
        if (!current() || this.state.phase !== 'recording') return
        durationMs = Math.min(MAX_RECORDING_MS, Math.max(0, this.environment.now() - startedAt))
        cancelTick()
        cancelLimit()
        cancelMute()
        this.show({ phase: 'finalizing', elapsedMs: durationMs, clip: null })
        try { recorder!.stop() } catch { fail('capture') }
        // stop() queues the final data/stop events; hardware access ends immediately.
        stopTracks(stream)
      }
      recorder.start()
      startedAt = this.environment.now()
      this.show({ phase: 'recording', elapsedMs: 0, clip: null })
      const tick = () => {
        if (!current() || this.state.phase !== 'recording') return
        const elapsedMs = Math.max(0, this.environment.now() - startedAt)
        if (elapsedMs >= MAX_RECORDING_MS) { this.stop(); return }
        this.show({ phase: 'recording', elapsedMs, clip: null })
        // The timer shows whole seconds, so emit only at each second boundary.
        cancelTick = this.environment.schedule(tick, 1000 - (elapsedMs % 1000))
      }
      cancelTick = this.environment.schedule(tick, 1000)
      cancelLimit = this.environment.schedule(() => this.stop(), MAX_RECORDING_MS)
    } catch (error) { fail(classify(error)) }
  }
}
