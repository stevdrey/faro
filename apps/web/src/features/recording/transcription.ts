// SPDX-License-Identifier: AGPL-3.0-or-later
import { AudioError, normalizeClip } from './normalize.ts'
import type { AudioErrorCode } from './normalize.ts'

export interface Transcription {
  readonly schemaVersion: 1
  readonly transcript: string
  readonly languageTag: string
  readonly engineId: string
  readonly modelId: string
  readonly modelVersion: string
  readonly latencyMs: number
  readonly audioDurationMs: number
  readonly status: 'TRANSCRIBED' | 'EMPTY' | 'UNRELIABLE' | 'UNAVAILABLE'
}
export interface TranscriptionState {
  readonly busy: boolean
  readonly result?: Transcription
  readonly error?: AudioErrorCode
}
export function readTranscription(value: unknown): Transcription {
  if (typeof value !== 'object' || !value) throw new AudioError('provider')
  const item = value as Record<string, unknown>
  if (item.schemaVersion !== 1 || typeof item.transcript !== 'string' || item.transcript.length > 16_000
    || !['languageTag', 'engineId', 'modelId', 'modelVersion'].every(key => typeof item[key] === 'string' && item[key].length > 0)
    || typeof item.latencyMs !== 'number' || !Number.isSafeInteger(item.latencyMs) || item.latencyMs < 0
    || typeof item.audioDurationMs !== 'number' || !Number.isSafeInteger(item.audioDurationMs)
    || item.audioDurationMs < 0 || item.audioDurationMs > 30_000
    || !['TRANSCRIBED', 'EMPTY', 'UNRELIABLE', 'UNAVAILABLE'].includes(item.status as string)
    || (item.status === 'TRANSCRIBED' && !item.transcript.trim())
    || ((item.status === 'EMPTY' || item.status === 'UNAVAILABLE') && item.transcript.trim())) {
    throw new AudioError('provider')
  }
  // An allowlist prevents unrecognized response properties from retaining media or private diagnostics.
  return Object.freeze({ schemaVersion: 1, transcript: item.transcript, languageTag: item.languageTag,
    engineId: item.engineId, modelId: item.modelId, modelVersion: item.modelVersion,
    latencyMs: item.latencyMs, audioDurationMs: item.audioDurationMs, status: item.status }) as Transcription
}
async function send(pcm: ArrayBuffer, signal: AbortSignal): Promise<Transcription> {
  let response: Response
  try {
    response = await fetch('/api/v1/speech/transcriptions', {
      method: 'POST', headers: { 'Content-Type': 'application/octet-stream' }, body: pcm,
      signal, cache: 'no-store', credentials: 'omit', redirect: 'error',
    })
  } catch { throw new AudioError('unavailable') }
  if (!response.ok) {
    throw new AudioError(response.status === 504 ? 'timeout'
      : response.status === 413 ? 'limit' : response.status === 415 || response.status === 400 ? 'invalid'
      : response.status === 502 ? 'provider' : 'unavailable')
  }
  try { return readTranscription(await response.json()) }
  catch { throw new AudioError('provider') }
}
interface Environment {
  normalize: (blob: Blob) => Promise<ArrayBuffer>
  send: (pcm: ArrayBuffer, signal: AbortSignal) => Promise<Transcription>
  schedule: (callback: () => void) => () => void
}
const browser: Environment = {
  normalize: normalizeClip, send,
  schedule: callback => { const timer = setTimeout(callback, 15_000); return () => clearTimeout(timer) },
}
/** Cancellation invalidates decoding as well as HTTP completion, including non-abortable late results. */
export class TranscriptionController {
  state: TranscriptionState = Object.freeze({ busy: false })
  private generation = 0
  private abort: AbortController | null = null
  private clearTimer = () => {}
  private listeners = new Set<(state: TranscriptionState) => void>()
  private readonly environment: Environment
  constructor(environment: Environment = browser) { this.environment = environment }
  subscribe(listener: (state: TranscriptionState) => void) {
    this.listeners.add(listener)
    return () => { this.listeners.delete(listener) }
  }
  private show(state: TranscriptionState) {
    this.state = Object.freeze(state)
    for (const listener of this.listeners) listener(this.state)
  }
  cancel() {
    this.generation++
    this.abort?.abort()
    this.abort = null
    this.clearTimer()
    this.show({ busy: false })
  }
  async start(blob: Blob) {
    if (this.state.busy) return
    this.cancel()
    const generation = this.generation
    const current = () => generation === this.generation
    const abort = new AbortController()
    this.abort = abort
    this.show({ busy: true })
    this.clearTimer = this.environment.schedule(() => {
      if (!current()) return
      this.cancel()
      this.show({ busy: false, error: 'timeout' })
    })
    try {
      const pcm = await this.environment.normalize(blob)
      if (!current()) return
      const result = await this.environment.send(pcm, abort.signal)
      if (current()) this.show({ busy: false, result })
    } catch (error) {
      if (current()) this.show({ busy: false, error: error instanceof AudioError ? error.code : 'provider' })
    } finally {
      if (current()) { this.clearTimer(); this.abort = null }
    }
  }
}
