// SPDX-License-Identifier: AGPL-3.0-or-later
import { useEffect, useRef, useState } from 'react'
import type { RefObject, SyntheticEvent } from 'react'
import { useAudioSession } from './AudioSession.tsx'

export type Playback = 'idle' | 'starting' | 'playing' | 'unavailable'
type AudioEvent = SyntheticEvent<HTMLAudioElement>

/** Invalidates pending play() promises, then stops and rewinds the clip. */
function halt(player: HTMLAudioElement, generation: RefObject<number>) {
  generation.current++
  player.pause()
  player.currentTime = 0
}

/**
 * One coordinated player: holds the application playback lease only while active, yields to
 * capture, and stops on hiding, pagehide or unmount. Spread `events` onto the `<audio>` element.
 */
export function useCoordinatedPlayback(audio: RefObject<HTMLAudioElement | null>) {
  const { coordinator, capturing } = useAudioSession()
  const lease = useRef<(() => void) | null>(null)
  const generation = useRef(0)
  const [playback, setPlayback] = useState<Playback>('idle')
  // Media events can arrive before React re-renders, so they read the latest state from a ref.
  const current = useRef<Playback>('idle')
  const show = (next: Playback) => { current.current = next; setPlayback(next) }
  const isActive = (value: Playback) => value === 'starting' || value === 'playing'
  const releaseLease = () => {
    lease.current?.()
    lease.current = null
  }

  useEffect(() => {
    // The ref is already detached during unmount cleanup, so keep the element.
    const player = audio.current!
    const stopForPage = () => {
      halt(player, generation)
      releaseLease()
      show('idle')
    }
    const stopWhenHidden = () => { if (document.hidden) stopForPage() }
    document.addEventListener('visibilitychange', stopWhenHidden)
    window.addEventListener('pagehide', stopForPage)
    return () => {
      halt(player, generation)
      releaseLease()
      document.removeEventListener('visibilitychange', stopWhenHidden)
      window.removeEventListener('pagehide', stopForPage)
    }
  }, [audio])

  function stop() {
    halt(audio.current!, generation)
    releaseLease()
    show('idle')
  }

  async function play() {
    stop()
    lease.current = coordinator?.play(stop) ?? null
    if (coordinator && !lease.current) return
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

  const events = {
    onEnded: (event: AudioEvent) => {
      // A previous playback's queued event can arrive after replay has rewound it.
      if (!event.currentTarget.ended) return
      generation.current++
      releaseLease()
      show('idle')
    },
    onPause: (event: AudioEvent) => {
      // Media keys, OS interruptions or a lost headset can pause without ending. A pause queued
      // by replay's own stop arrives after play() has already cleared `paused`; pauses queued by
      // stop, hiding or an error arrive once playback is no longer active. Both are ignored.
      // Bumping the generation keeps a still-pending play() rejection from reporting a failure.
      if (!isActive(current.current) || !event.currentTarget.paused) return
      generation.current++
      releaseLease()
      show('idle')
    },
    onPlaying: (event: AudioEvent) => {
      if (event.currentTarget.paused) return
      if (coordinator?.isCapturing()) { stop(); return }
      lease.current ??= coordinator?.play(stop) ?? null
      show('playing')
    },
    onError: () => { stop(); show('unavailable') },
  }

  return { playback, active: isActive(playback), capturing, play, stop, events }
}
