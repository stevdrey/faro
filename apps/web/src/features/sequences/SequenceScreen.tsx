// SPDX-License-Identifier: AGPL-3.0-or-later
import { useReducer, useRef, useState } from 'react'
import type { ActionTracePayload } from '../../contracts/action.ts'
import type { SequenceExercise } from '../../contracts/exercise.ts'
import { preventRepeatedActivation, useCompletionNotice } from '../shared/completion.ts'
import { canJump, emptySequenceAttempt, JUMP_SIZES, sequenceAttemptReducer, visibleJumps } from './attempt.ts'
import type { JumpKind, JumpSize } from './attempt.ts'
import { NumberLine } from './NumberLine.tsx'
import { useExerciseGuidance } from '../guidance/SpokenGuidance.tsx'

interface Props {
  exercise: SequenceExercise
  onComplete: (trace: ActionTracePayload) => void
  onReset: () => void
}

/** Mount with key={exercise.exerciseId} when changing exercises. */
export function SequenceScreen({ exercise, onComplete, onReset }: Props) {
  const terms = exercise.operands.terms
  const [state, dispatch] = useReducer(sequenceAttemptReducer, terms[terms.length - 1], emptySequenceAttempt)
  const [size, setSize] = useState<JumpSize>(1)
  const { guidance, restartGuidance } = useExerciseGuidance(exercise, state.completed)
  const resetButton = useRef<HTMLButtonElement>(null)
  const jumps = visibleJumps(state.trace)

  useCompletionNotice(state.completed, state.trace, onComplete, resetButton)

  function jump(kind: JumpKind) {
    dispatch({ kind, value: size, occurredAt: new Date().toISOString() })
  }

  return <section className="activity sequence-activity" aria-labelledby="page-title" data-completed={state.completed}>
    <header className="activity-header">
      <a className="brand" href="#page-title" aria-label="Faro, inicio"><span aria-hidden="true">✦</span> faro</a>
      <span className="activity-label">EXPLORÁ CON SALTOS</span>
    </header>
    <div className="intro">
      <p className="eyebrow">VAMOS A EXPLORAR</p>
      <h1 id="page-title">¿Qué número sigue?</h1>
      <p>Elegí un salto y movete. Podés cambiar de idea.</p>
    </div>
    {guidance}
    <div className="sequence-terms" role="group" aria-label="Continuá la secuencia" data-testid="sequence-terms">
      {terms.map((term, index) => <span key={index}>{term}<span aria-hidden="true">,</span></span>)}
      <span className="missing-symbol" role="img" aria-label="Número que sigue">□</span>
    </div>
    <fieldset className="jump-sizes" disabled={state.completed}>
      <legend>Tamaño del salto</legend>
      {JUMP_SIZES.map(value => <label key={value}>
        <input type="radio" name="jump-size" value={value} checked={size === value} onChange={() => setSize(value)} />
        <span>{value}</span>
      </label>)}
    </fieldset>
    <div className="jump-controls" role="group" aria-label="Saltar en la recta">
      <button type="button" aria-label={`Saltar ${size} a la izquierda`} aria-disabled={state.completed || !canJump(state.position, 'jumpLeft', size)}
        onKeyDown={preventRepeatedActivation} onClick={() => jump('jumpLeft')}><span aria-hidden="true">←</span> {size}</button>
      <p role="status" aria-atomic="true">{state.completed ? 'Terminaste' : `Estás en ${state.position}`}</p>
      <button type="button" aria-label={`Saltar ${size} a la derecha`} aria-disabled={state.completed || !canJump(state.position, 'jumpRight', size)}
        onKeyDown={preventRepeatedActivation} onClick={() => jump('jumpRight')}>{size} <span aria-hidden="true">→</span></button>
    </div>
    <NumberLine terms={terms} position={state.position} jumps={jumps} />
    <section className="jump-history" aria-labelledby="jumps-title">
      <h2 id="jumps-title">Tus saltos</h2>
      {jumps.length === 0 ? <p>Todavía no diste saltos.</p> : <ol>{jumps.map(({ from, to, event }) =>
        <li key={event.step}>De {from} a {to}: {event.value} a la {event.kind === 'jumpLeft' ? 'izquierda' : 'derecha'}.</li>,
      )}</ol>}
    </section>
    <footer className="activity-footer sequence-footer">
      <button type="button" ref={resetButton} className="restart-button" aria-disabled={state.trace.events.length === 0}
        onKeyDown={preventRepeatedActivation}
        onClick={() => {
          if (state.trace.events.length === 0) return
          dispatch({ kind: 'reset' })
          setSize(1)
          restartGuidance()
          onReset()
        }}>Empezar de nuevo <span aria-hidden="true">↻</span></button>
      <button type="button" className="finish-button" aria-disabled={state.completed}
        onKeyDown={preventRepeatedActivation}
        onMouseDown={event => { if (state.completed) event.preventDefault() }}
        onClick={() => dispatch({ kind: 'finishAttempt', occurredAt: new Date().toISOString() })}>Listo <span aria-hidden="true">→</span></button>
    </footer>
    <p className="privacy-note">Demo local · No graba audio. Los movimientos se borran al reiniciar, cambiar de actividad o cerrar.</p>
  </section>
}
