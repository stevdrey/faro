// SPDX-License-Identifier: AGPL-3.0-or-later
import { useEffect, useRef, useState } from 'react'
import { clips, clipSource } from './clips.ts'
import type { ClipId } from './clips.ts'
import { preventRepeatedActivation } from '../shared/completion.ts'

type Playback = 'idle' | 'starting' | 'playing' | 'unavailable'

/** Mount with a new key on exercise/phase/restart changes to cancel pending playback. */
export function SpokenGuidance({ clip }: { clip: ClipId }) {
  const audio = useRef<HTMLAudioElement>(null)
  const generation = useRef(0)
  const [playback, setPlayback] = useState<Playback>('idle')

  useEffect(() => {
    const player = audio.current!
    const stopWhenHidden = () => {
      if (document.hidden) {
        generation.current++
        player.pause()
        player.currentTime = 0
        setPlayback('idle')
      }
    }
    document.addEventListener('visibilitychange', stopWhenHidden)
    return () => {
      generation.current++
      player.pause()
      player.currentTime = 0
      document.removeEventListener('visibilitychange', stopWhenHidden)
    }
  }, [])

  function stop() {
    generation.current++
    audio.current!.pause()
    audio.current!.currentTime = 0
    setPlayback('idle')
  }

  async function play() {
    stop()
    const request = ++generation.current
    setPlayback('starting')
    try {
      if (audio.current!.error) audio.current!.load()
      await audio.current!.play()
      if (request === generation.current) setPlayback('playing')
    } catch {
      // Interrupted play() promises may settle after stop, restart or unmount.
      if (request === generation.current) setPlayback('unavailable')
    }
  }

  return <div className="spoken-guidance" role="group" aria-label="Guía de voz">
    <audio ref={audio} src={clipSource(clip, import.meta.env.BASE_URL)} preload="none"
      onEnded={() => { generation.current++; setPlayback('idle') }}
      onError={() => { stop(); setPlayback('unavailable') }} />
    <div className="guidance-controls">
      <button type="button" aria-label="Escuchar o repetir guía" onKeyDown={preventRepeatedActivation} onClick={play}>
        <span aria-hidden="true">🔊</span> Escuchar guía
      </button>
      <button type="button" aria-label="Detener guía" title="Detener guía" disabled={playback !== 'starting' && playback !== 'playing'} onClick={stop}>
        <span aria-hidden="true">■</span>
      </button>
    </div>
    <p className="guidance-state" aria-live="polite">{playback === 'unavailable'
      ? 'No se pudo escuchar. Podés intentarlo otra vez o seguir jugando.'
      : playback === 'starting' ? 'Preparando la guía…' : playback === 'playing' ? 'Escuchando la guía…' : ''}</p>
    <details><summary>Ver instrucciones</summary><p>{clips[clip]}</p></details>
  </div>
}
