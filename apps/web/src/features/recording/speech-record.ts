// SPDX-License-Identifier: AGPL-3.0-or-later
import type { CaptureError, CaptureState } from './capture.ts'
import { readTranscription } from './transcription.ts'
import type { Transcription, TranscriptionState } from './transcription.ts'
import type { AudioErrorCode } from './normalize.ts'

/** One current observation, never a media container or retry history. */
export type SpeechRecord = Readonly<{ attemptId: string } & (
  | { stage: 'capture'; status: 'ready'; recordingDurationMs: number }
  | { stage: 'capture'; status: 'error'; error: CaptureError }
  | { stage: 'asr'; status: 'result'; recordingDurationMs: number; hypothesis: Transcription }
  | { stage: 'asr'; status: 'error'; recordingDurationMs: number; error: AudioErrorCode; audioDurationMs?: number }
)>

/** Project only owned fields: no Blob, vendor exception, or arbitrary extra property can be retained. */
export function captureRecord(attemptId: string, state: CaptureState): SpeechRecord | null {
  if (state.phase === 'ready' && state.clip) {
    return Object.freeze({ attemptId, stage: 'capture', status: 'ready', recordingDurationMs: state.clip.durationMs })
  }
  if (state.phase === 'error' && state.error) {
    return Object.freeze({ attemptId, stage: 'capture', status: 'error', error: state.error })
  }
  return null
}

export function transcriptionRecord(attemptId: string, recordingDurationMs: number, state: TranscriptionState): SpeechRecord | null {
  if (state.result) return Object.freeze({ attemptId, stage: 'asr', status: 'result', recordingDurationMs,
    hypothesis: readTranscription(state.result) })
  if (state.error) return Object.freeze({ attemptId, stage: 'asr', status: 'error', recordingDurationMs,
    error: state.error, ...(state.audioDurationMs === undefined ? {} : { audioDurationMs: state.audioDurationMs }) })
  return null
}
