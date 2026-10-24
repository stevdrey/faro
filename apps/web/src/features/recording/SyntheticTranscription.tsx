// SPDX-License-Identifier: AGPL-3.0-or-later
import { useEffect, useState } from 'react'
import type { RecordedClip } from './capture.ts'
import { TranscriptionController } from './transcription.ts'
import type { AudioErrorCode } from './normalize.ts'
import { preventRepeatedActivation } from '../shared/completion.ts'

const messages: Record<AudioErrorCode, string> = {
  unsupported: 'Este formato de audio no se puede transcribir aquí. Podés volver a grabar o seguir con la actividad.',
  invalid: 'No se pudo leer este audio. Podés volver a grabar.',
  limit: 'El audio supera el límite de la prueba. Podés grabar uno más corto.',
  unavailable: 'La transcripción local no está disponible. Podés reintentar o seguir con la actividad.',
  timeout: 'La transcripción tardó demasiado. Podés reintentar o seguir con la actividad.',
  provider: 'No se pudo obtener una transcripción. Podés reintentar o seguir con la actividad.',
}

/** Explicitly enabled technical preview, pending FARO-040; not an adult review or a child-audio gate. */
export function SyntheticTranscription({ clip }: { clip: RecordedClip }) {
  const [controller] = useState(() => new TranscriptionController())
  const [state, setState] = useState(controller.state)
  const [confirmed, setConfirmed] = useState(false)
  useEffect(() => {
    const unsubscribe = controller.subscribe(setState)
    // Hiding/pagehide cancel the parent capture, which drops the clip and unmounts this preview.
    return () => {
      unsubscribe()
      controller.cancel()
    }
  }, [controller])
  return <div className="synthetic-transcription">
    <p>Prueba técnica: solo audio sintético. La integración con audio infantil está pendiente de LOCAL_ONLY (issue #21).</p>
    <label><input type="checkbox" checked={confirmed} onChange={event => {
      setConfirmed(event.target.checked)
      if (!event.target.checked) controller.cancel()
    }} /> Confirmo que esta grabación contiene solo audio sintético.</label>
    <button type="button" disabled={!confirmed || state.busy} onKeyDown={preventRepeatedActivation}
      onClick={() => { if (confirmed && !document.hidden) void controller.start(clip.blob) }}>Transcribir prueba</button>
    {state.busy && <button type="button" onClick={() => controller.cancel()}>Cancelar transcripción</button>}
    <p role="status" aria-live="polite">{state.busy ? 'Transcribiendo localmente…' : state.error ? messages[state.error]
      : state.result?.status === 'TRANSCRIBED' ? 'Transcripción lista, sin confirmar.'
      : state.result?.status === 'EMPTY' ? 'No se reconocieron palabras. Podés volver a grabar.'
      : state.result?.status === 'UNRELIABLE' ? 'La transcripción es incierta. Podés volver a grabar.'
      : state.result ? messages.unavailable : ''}</p>
    {state.result?.transcript && <details><summary>Ver hipótesis sin confirmar (prueba técnica)</summary>
      <p>{state.result.transcript}</p>
      <p>Idioma efectivo: {state.result.languageTag}. Motor: {state.result.engineId}. Tiempo: {state.result.latencyMs} ms.</p>
    </details>}
  </div>
}
