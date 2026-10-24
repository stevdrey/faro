// SPDX-License-Identifier: AGPL-3.0-or-later
import { useReducer, useRef } from 'react'
import { clips, clipSource, exerciseClip } from './clips.ts'
import type { ClipId } from './clips.ts'
import type { Exercise } from '../../contracts/exercise.ts'
import { useCoordinatedPlayback } from '../recording/use-coordinated-playback.ts'
import { preventRepeatedActivation } from '../shared/completion.ts'

/**
 * Renders the guidance for an exercise screen. Call restartGuidance when an unfinished attempt
 * restarts; finishing, restarting and changing clips remount the player, cancelling old playback.
 */
export function useExerciseGuidance(exercise: Exercise, completed: boolean) {
  const [attempt, restartGuidance] = useReducer((value: number) => value + 1, 0)
  const clip = completed ? 'next' : exerciseClip(exercise)
  const guidance = clip && <SpokenGuidance key={`${attempt}-${clip}`} clip={clip} />
  return { guidance, restartGuidance }
}

/** Mount with a new key on exercise/phase/restart changes to cancel pending playback. */
export function SpokenGuidance({ clip, label = 'Escuchar guía' }: { clip: ClipId; label?: string }) {
  const audio = useRef<HTMLAudioElement>(null)
  const { playback, active, capturing, play, stop, events } = useCoordinatedPlayback(audio)

  return <div className="spoken-guidance" role="group" aria-label="Guía de voz">
    <audio ref={audio} src={clipSource(clip, import.meta.env.BASE_URL)} preload="none" {...events} />
    <div className="guidance-controls">
      <button type="button" onKeyDown={preventRepeatedActivation} aria-disabled={capturing} onClick={play}>
        <span aria-hidden="true">🔊</span> {label}
      </button>
      {/* aria-disabled keeps the control focused after it stops the clip. */}
      <button type="button" aria-label="Detener guía" title="Detener guía" aria-disabled={!active}
        onClick={() => { if (active) stop() }}>
        <span aria-hidden="true">■</span>
      </button>
    </div>
    {/* Progress is visual only, so a screen reader does not talk over the clip. */}
    <p className="guidance-state">{playback === 'starting' ? 'Preparando la guía…' : playback === 'playing' ? 'Escuchando la guía…' : ''}</p>
    <p className="guidance-state" aria-live="polite">{playback === 'unavailable'
      ? 'No se pudo escuchar. Podés intentarlo otra vez o seguir jugando.' : ''}</p>
    <details><summary>Ver instrucciones</summary><p>{clips[clip]}</p></details>
  </div>
}
