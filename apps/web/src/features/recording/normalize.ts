// SPDX-License-Identifier: AGPL-3.0-or-later
import { MAX_RECORDING_MS } from './capture.ts'

export const ASR_SAMPLE_RATE = 16_000
export const MAX_PCM_BYTES = ASR_SAMPLE_RATE * 2 * MAX_RECORDING_MS / 1000
export const MAX_ENCODED_BYTES = 8 * 1024 * 1024
/** The recorder's 30 s auto-stop timer and codec padding overrun slightly; that tail is trimmed, not rejected. */
export const OVERRUN_TOLERANCE_MS = 1_000
const MAX_DECODED_SAMPLES = ASR_SAMPLE_RATE * (MAX_RECORDING_MS + OVERRUN_TOLERANCE_MS) / 1000
export type AudioErrorCode = 'unsupported' | 'invalid' | 'limit' | 'unavailable' | 'timeout' | 'provider'
/** Only a stable code crosses the audio boundary; browser/vendor errors may contain private data. */
export class AudioError extends Error {
  readonly code: AudioErrorCode
  constructor(code: AudioErrorCode) { super(code); this.code = code }
}

/** Web Audio has already resampled each channel to 16 kHz. Average channels, saturate, trim to 30 s, encode LE. */
export function encodePcm(channels: readonly Float32Array[]): ArrayBuffer {
  const decoded = channels[0]?.length ?? 0
  if (!decoded || channels.some(channel => channel.length !== decoded)) throw new AudioError('invalid')
  if (decoded > MAX_DECODED_SAMPLES) throw new AudioError('limit')
  const length = Math.min(decoded, MAX_PCM_BYTES / 2)
  const result = new ArrayBuffer(length * 2)
  const view = new DataView(result)
  for (let index = 0; index < length; index++) {
    let sample = 0
    for (const channel of channels) {
      if (!Number.isFinite(channel[index])) throw new AudioError('invalid')
      sample += channel[index] / channels.length
    }
    sample = Math.max(-1, Math.min(1, sample))
    view.setInt16(index * 2, Math.round(sample * (sample < 0 ? 32768 : 32767)), true)
  }
  return result
}

/** Decode the complete native MediaRecorder file in memory. No AudioContext playback or external codec. */
export async function normalizeClip(blob: Blob): Promise<ArrayBuffer> {
  if (!blob.size) throw new AudioError('invalid')
  if (blob.size > MAX_ENCODED_BYTES) throw new AudioError('limit')
  if (!globalThis.OfflineAudioContext) throw new AudioError('unsupported')
  let decoded: AudioBuffer
  try {
    decoded = await new OfflineAudioContext(1, 1, ASR_SAMPLE_RATE).decodeAudioData(await blob.arrayBuffer())
  } catch { throw new AudioError('unsupported') }
  if (decoded.sampleRate !== ASR_SAMPLE_RATE) throw new AudioError('unsupported')
  return encodePcm(Array.from({ length: decoded.numberOfChannels }, (_, index) => decoded.getChannelData(index)))
}
