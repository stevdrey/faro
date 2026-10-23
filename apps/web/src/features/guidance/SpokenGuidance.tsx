// SPDX-License-Identifier: AGPL-3.0-or-later
import { useEffect, useReducer, useRef, useState } from 'react'
import type { RefObject } from 'react'
import { clips, clipSource, exerciseClip } from './clips.ts'
import type { ClipId } from './clips.ts'
import type { Exercise } from '../../contracts/exercise.ts'
import { useAudioSession } from '../recording/AudioSession.tsx'
import { preventRepeatedActivation } from '../shared/completion.ts'

type Playback = 'idle' | 'starting' | 'playing' | 'unavailable'

/** Invalidates pending play() promises, then stops and rewinds the clip. */
function halt(player: HTMLAudioElement, generation: RefObject<number>) {
  generation.current++
  player.pause()
  player.currentTime = 0
}

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
  const { coordinator, capturing } = useAudioSession()
  const release = useRef<(() => void) | null>(null)
  const audio = useRef<HTMLAudioElement>(null)
  const generation = useRef(0)
  const [playback, setPlayback] = useState<Playback>('idle')
  // Media events can arrive before React re-renders, so they read the latest state from a ref.
  const current = useRef<Playback>('idle')
  const show = (next: Playback) => { current.current = next; setPlayback(next) }
  const isActive = (value: Playback) => value === 'starting' || value === 'playing'
  const active = isActive(playback)

  useEffect(() => {
    // The ref is already detached during unmount cleanup, so keep the element.
    const player = audio.current!
    const stopForPage = () => {
      halt(player, generation)
      release.current?.()
      release.current = null
      show('idle')
    }
    const stopWhenHidden = () => { if (document.hidden) stopForPage() }
    document.addEventListener('visibilitychange', stopWhenHidden)
    window.addEventListener('pagehide', stopForPage)
    return () => {
      halt(player, generation)
      release.current?.()
      release.current = null
      document.removeEventListener('visibilitychange', stopWhenHidden)
      window.removeEventListener('pagehide', stopForPage)
    }
  }, [])

  function stop() {
    halt(audio.current!, generation)
    release.current?.()
    release.current = null
    show('idle')
  }

  async function play() {
    stop()
    release.current = coordinator?.play(stop) ?? null
    if (coordinator && !release.current) return
    const request = ++generation.current
    show('starting')
    try {
      if (audio.current!.error) audio.current!.load()
      await audio.current!.play()
      if (request === generation.current) show('playing')
    } catch {
      // Interrupted play() promises may settle after stop, restart or unmount.
      if (request === generation.current) { stop(); show('unavailable') }
    }
  }

  return <div className="spoken-guidance" role="group" aria-label="Guía de voz">
    <audio ref={audio} src={clipSource(clip, import.meta.env.BASE_URL)} preload="none"
      onEnded={event => {
        // A previous playback's queued event can arrive after replay has rewound it.
        if (!event.currentTarget.ended) return
        generation.current++
        release.current?.()
        release.current = null
        show('idle')
      }}
      onPause={event => {
        // Media keys, OS interruptions or a lost headset can pause without ending. A pause queued
        // by replay's own stop arrives after play() has already cleared `paused`; pauses queued by
        // stop, hiding or an error arrive once playback is no longer active. Both are ignored.
        if (!isActive(current.current) || !event.currentTarget.paused) return
        generation.current++
        release.current?.()
        release.current = null
        show('idle')
      }}
      onPlaying={event => {
        if (event.currentTarget.paused) return
        if (coordinator?.isCapturing()) { stop(); return }
        release.current ??= coordinator?.play(stop) ?? null
        show('playing')
      }}
      onError={() => { stop(); show('unavailable') }} />
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
