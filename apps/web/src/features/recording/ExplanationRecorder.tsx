// SPDX-License-Identifier: AGPL-3.0-or-later
import { useEffect, useRef, useState } from 'react'
import { CaptureController, isInFlight } from './capture.ts'
import type { CaptureError, RecordedClip } from './capture.ts'
import { useAudioSession } from './AudioSession.tsx'
import { useCoordinatedPlayback } from './use-coordinated-playback.ts'
import { SpokenGuidance } from '../guidance/SpokenGuidance.tsx'
import { clips } from '../guidance/clips.ts'
import { preventRepeatedActivation } from '../shared/completion.ts'
import { SyntheticTranscription } from './SyntheticTranscription.tsx'
import { TranscriptionController } from './transcription.ts'
import type { TranscriptionState } from './transcription.ts'
import { speechRecord } from './speech-record.ts'
import type { SpeechRecord } from './speech-record.ts'

const synthetic = import.meta.env.VITE_FARO_SYNTHETIC_ASR === 'true'
const isIdle = (state: TranscriptionState) => !state.busy && !state.result && !state.error

const messages: Record<CaptureError, string> = {
  permission: 'El micrófono no tiene permiso. Pedile ayuda a la persona adulta o seguí jugando.',
  device: 'No se pudo usar el micrófono. Podés revisar la conexión e intentarlo otra vez.',
  unsupported: 'Este navegador no permite grabar aquí. Podés seguir jugando sin grabar.',
  interrupted: 'La grabación se interrumpió y se borró. Podés intentarlo otra vez.',
  empty: 'No quedó una grabación. Podés intentarlo otra vez.',
  capture: 'No se pudo grabar. Podés intentarlo otra vez o seguir jugando.',
}

function RecordedPlayback({ clip }: { clip: RecordedClip }) {
  const audio = useRef<HTMLAudioElement>(null)
  const { playback, active, play, stop, events } = useCoordinatedPlayback(audio)
  useEffect(() => {
    const player = audio.current!
    const url = URL.createObjectURL(clip.blob)
    player.src = url
    return () => {
      player.pause()
      player.removeAttribute('src')
      player.load()
      URL.revokeObjectURL(url)
    }
  }, [clip])
  return <div className="recording-playback">
    <audio ref={audio} preload="none" {...events} />
    <button type="button" onKeyDown={preventRepeatedActivation} onClick={play}>Escuchar mi grabación</button>
    <button type="button" aria-disabled={!active} onClick={() => { if (active) stop() }}>Detener reproducción</button>
    <p role="status">{playback === 'unavailable' ? 'No se pudo escuchar. Podés intentarlo otra vez o borrar la grabación.' : active ? 'Escuchando tu grabación…' : ''}</p>
  </div>
}

/** Mounted only for one completed attempt; authorization is owned by the open application. */
export function ExplanationRecorder({ authorized, authorize, attemptId, recordSpeech }: {
  authorized: boolean; authorize: () => void; attemptId: string
  recordSpeech: (attemptId: string, record: SpeechRecord | null) => void
}) {
  const [controller] = useState(() => new CaptureController())
  const [transcription] = useState(() => new TranscriptionController())
  const [state, setState] = useState(controller.state)
  const [confirmed, setConfirmed] = useState(false)
  const { coordinator } = useAudioSession()
  const release = useRef<(() => void) | null>(null)
  const recordButton = useRef<HTMLButtonElement>(null)
  const busy = isInFlight(state.phase)
  const releaseCapture = () => {
    release.current?.()
    release.current = null
  }
  useEffect(() => {
    // The single writer of the record: ASR outcomes supersede the ready clip's capture record.
    const publish = () => recordSpeech(attemptId, speechRecord(attemptId, controller.state, transcription.state))
    let phase = controller.state.phase
    const unsubscribeCapture = controller.subscribe(next => {
      // Every path that drops the clip invalidates ASR here, before a response continuation or React unmount.
      if (next.phase !== 'ready' && !isIdle(transcription.state)) transcription.cancel()
      setState(next)
      // Recording ticks repeat the phase and cannot change the record.
      if (next.phase !== phase) { phase = next.phase; publish() }
      if (!isInFlight(next.phase)) releaseCapture()
    })
    const unsubscribeTranscription = transcription.subscribe(publish)
    // Players stop themselves on hiding/pagehide; cancelling also unmounts the clip player and invalidates ASR.
    const clear = () => controller.cancel()
    const hidden = () => { if (document.hidden) clear() }
    document.addEventListener('visibilitychange', hidden)
    window.addEventListener('pagehide', clear)
    return () => {
      unsubscribeCapture()
      unsubscribeTranscription()
      controller.cancel()
      transcription.cancel()
      recordSpeech(attemptId, null)
      releaseCapture()
      document.removeEventListener('visibilitychange', hidden)
      window.removeEventListener('pagehide', clear)
    }
  }, [controller, transcription, coordinator, attemptId, recordSpeech])
  function start() {
    // Read the controller synchronously: multiple events may precede React's next render.
    if (isInFlight(controller.state.phase) || !authorized || document.hidden) return
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
      <p className="recording-timer" role="timer" aria-label="Tiempo de grabación">{Math.floor(state.elapsedMs / 1000)} / 30 s</p>
      {state.phase === 'recording' && <p>{clips.recordingStop}</p>}
      {state.clip && <RecordedPlayback clip={state.clip} />}
      {state.clip && synthetic && <SyntheticTranscription clip={state.clip} controller={transcription} />}
    </>}
    <p className="privacy-note">El audio queda en memoria. Se borra al cancelar, borrar, repetir, reiniciar, cambiar de actividad, ocultar o cerrar la página. {synthetic
      ? 'Solo «Transcribir prueba» envía el audio normalizado al servidor local para transcribirlo; no se guarda. Usá solo audio sintético.'
      : 'No se envía ni se transcribe.'}</p>
  </section>
}
