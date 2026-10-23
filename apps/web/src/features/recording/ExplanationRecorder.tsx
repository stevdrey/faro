// SPDX-License-Identifier: AGPL-3.0-or-later
import { useEffect, useRef, useState } from 'react'
import { CaptureController } from './capture.ts'
import type { CaptureError, RecordedClip } from './capture.ts'
import { useAudioSession } from './AudioSession.tsx'
import { SpokenGuidance } from '../guidance/SpokenGuidance.tsx'
import { clips } from '../guidance/clips.ts'
import { preventRepeatedActivation } from '../shared/completion.ts'

const messages: Record<CaptureError, string> = {
  permission: 'El micrófono no tiene permiso. Pedile ayuda a la persona adulta o seguí jugando.',
  device: 'No se pudo usar el micrófono. Podés revisar la conexión e intentarlo otra vez.',
  unsupported: 'Este navegador no permite grabar aquí. Podés seguir jugando sin grabar.',
  interrupted: 'La grabación se interrumpió y se borró. Podés intentarlo otra vez.',
  empty: 'No quedó una grabación. Podés intentarlo otra vez.',
  capture: 'No se pudo grabar. Podés intentarlo otra vez o seguir jugando.',
}

function RecordedPlayback({ clip }: { clip: RecordedClip }) {
  const { coordinator } = useAudioSession()
  const audio = useRef<HTMLAudioElement>(null)
  const release = useRef<(() => void) | null>(null)
  const generation = useRef(0)
  const [playing, setPlaying] = useState(false)
  const [failed, setFailed] = useState(false)
  function stop() {
    generation.current++
    audio.current?.pause()
    if (audio.current) audio.current.currentTime = 0
    release.current?.()
    release.current = null
    setPlaying(false)
  }
  useEffect(() => {
    const player = audio.current!
    const url = URL.createObjectURL(clip.blob)
    player.src = url
    return () => {
      generation.current++
      player.pause()
      player.removeAttribute('src')
      player.load()
      URL.revokeObjectURL(url)
      release.current?.()
      release.current = null
    }
  }, [clip])
  async function play() {
    stop()
    release.current = coordinator?.play(stop) ?? null
    if (coordinator && !release.current) return
    const request = ++generation.current
    setFailed(false)
    setPlaying(true)
    try {
      await audio.current!.play()
    } catch {
      if (request === generation.current) { stop(); setFailed(true) }
    }
  }
  return <div className="recording-playback">
    <audio ref={audio} preload="none" onEnded={event => { if (event.currentTarget.ended) stop() }}
      onPause={event => { if (event.currentTarget.paused) { setPlaying(false); release.current?.(); release.current = null } }}
      onPlaying={event => {
        if (event.currentTarget.paused) return
        if (coordinator?.isCapturing()) stop()
        else { release.current ??= coordinator?.play(stop) ?? null; setPlaying(true) }
      }} onError={() => { stop(); setFailed(true) }} />
    <button type="button" onKeyDown={preventRepeatedActivation} onClick={play}>Escuchar mi grabación</button>
    <button type="button" aria-disabled={!playing} onClick={() => { if (playing) stop() }}>Detener reproducción</button>
    <p role="status">{failed ? 'No se pudo escuchar. Podés intentarlo otra vez o borrar la grabación.' : playing ? 'Escuchando tu grabación…' : ''}</p>
  </div>
}

/** Mounted only for one completed attempt; authorization is owned by the open application. */
export function ExplanationRecorder({ authorized, authorize }: { authorized: boolean; authorize: () => void }) {
  const [controller] = useState(() => new CaptureController())
  const [state, setState] = useState(controller.state)
  const [confirmed, setConfirmed] = useState(false)
  const { coordinator } = useAudioSession()
  const release = useRef<(() => void) | null>(null)
  const recordButton = useRef<HTMLButtonElement>(null)
  const busy = ['requesting', 'recording', 'finalizing'].includes(state.phase)
  useEffect(() => {
    const unsubscribe = controller.subscribe(next => {
      setState(next)
      if (!['requesting', 'recording', 'finalizing'].includes(next.phase)) {
        release.current?.()
        release.current = null
      }
    })
    const clear = () => { coordinator?.stopPlayback(); controller.cancel() }
    const hidden = () => { if (document.hidden) clear() }
    document.addEventListener('visibilitychange', hidden)
    window.addEventListener('pagehide', clear)
    return () => {
      unsubscribe()
      controller.cancel()
      release.current?.()
      release.current = null
      document.removeEventListener('visibilitychange', hidden)
      window.removeEventListener('pagehide', clear)
    }
  }, [controller, coordinator])
  function start() {
    // Read the controller synchronously: multiple events may precede React's next render.
    if (['requesting', 'recording', 'finalizing'].includes(controller.state.phase) || !authorized || document.hidden) return
    const lease = coordinator?.record() ?? null
    if (coordinator && !lease) return
    release.current = lease
    void controller.start()
  }
  return <section className="explanation-recorder" aria-labelledby="recording-title">
    <h2 id="recording-title">Contame cómo lo hiciste</h2>
    <p>Si querés, podés grabar una explicación de hasta 30 segundos.</p>
    {!authorized ? <div className="recording-authorization">
      <p>Para la persona adulta: habilitá esta función solo para una prueba local supervisada. Esta confirmación no sustituye el consentimiento documentado del tutor para el piloto.</p>
      <label><input type="checkbox" checked={confirmed} onChange={event => setConfirmed(event.target.checked)} /> Confirmo que acompaño y autorizo esta prueba local de voz.</label>
      <button type="button" disabled={!confirmed} onClick={authorize}>Habilitar grabación</button>
    </div> : <>
      <SpokenGuidance clip="recordingStart" label="Escuchar cómo grabar" />
      <div className="recording-controls">
        <button type="button" ref={recordButton} onKeyDown={preventRepeatedActivation}
          aria-disabled={state.phase === 'requesting' || state.phase === 'finalizing'}
          onClick={() => { if (state.phase === 'recording') controller.stop(); else start() }}>
          <span aria-hidden="true">{state.phase === 'recording' ? '■' : '🎙'}</span>{' '}
          {state.phase === 'recording' ? 'Detener grabación' : state.clip ? 'Grabar de nuevo' : 'Grabar explicación'}
        </button>
        {busy && <button type="button" onClick={() => { controller.cancel(); recordButton.current?.focus() }}>Cancelar</button>}
        {state.clip && <button type="button" onClick={() => { controller.cancel(); recordButton.current?.focus() }}>Borrar</button>}
      </div>
      <p className="recording-indicator" role="status" aria-atomic="true">
        {state.phase === 'requesting' ? 'Esperando permiso del micrófono…'
          : state.phase === 'recording' ? '● Grabando'
          : state.phase === 'finalizing' ? 'Terminando la grabación…'
          : state.phase === 'ready' ? 'Grabación lista. El micrófono está apagado.'
          : state.phase === 'error' ? messages[state.error!] : 'Micrófono apagado. Vos elegís cuándo empezar.'}
      </p>
      <p className="recording-timer" aria-label="Tiempo de grabación">{Math.floor(state.elapsedMs / 1000)} / 30 s</p>
      {state.phase === 'recording' && <p>{clips.recordingStop}</p>}
      {state.clip && <RecordedPlayback clip={state.clip} />}
    </>}
    <p className="privacy-note">El audio queda solo en memoria en esta página. Se borra al cancelar, borrar, repetir, reiniciar, cambiar de actividad, ocultar o cerrar la página. No se envía ni se transcribe.</p>
  </section>
}
