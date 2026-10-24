// SPDX-License-Identifier: AGPL-3.0-or-later
import { useReducer, useRef } from 'react'
import type { MouseEvent as ReactMouseEvent } from 'react'
import type { ActionTracePayload } from '../../contracts/action.ts'
import type { MissingAddendExercise } from '../../contracts/exercise.ts'
import { preventRepeatedActivation, useCompletionNotice } from '../shared/completion.ts'
import { usePieceDrag } from '../shared/piece-drag.ts'
import { useExerciseGuidance } from '../guidance/SpokenGuidance.tsx'
import { attemptReducer, emptyAttempt, MAX_BLOCKS } from './attempt.ts'

/** Multi-clicks this soon after finishing/resetting are treated as accidental repeats. */
const TRANSITION_GUARD_MS = 400

type MoveKind = 'placeBlock' | 'removeBlock'
interface Props {
  exercise: MissingAddendExercise
  onComplete: (trace: ActionTracePayload) => void
  onReset: () => void
}

export function MissingAddendScreen({ exercise, onComplete, onReset }: Props) {
  const [state, dispatch] = useReducer(attemptReducer, undefined, emptyAttempt)
  const { guidance } = useExerciseGuidance(exercise, state.completed)
  const answerArea = useRef<HTMLDivElement>(null)
  const reserveArea = useRef<HTMLDivElement>(null)
  const resetButton = useRef<HTMLButtonElement>(null)
  const lastTransitionAt = useRef(Number.NEGATIVE_INFINITY)

  useCompletionNotice(state.completed, state.trace, onComplete, resetButton)

  const { drag, preview, cancelDrag, pieceHandlers } = usePieceDrag<MoveKind>({
    canStart: kind => !state.completed && (kind === 'placeBlock' ? state.count < MAX_BLOCKS : state.count > 0),
    dropTarget: kind => kind === 'placeBlock' ? answerArea.current : reserveArea.current,
    onDrop: move,
  })

  function move(kind: MoveKind | 'finishAttempt') {
    cancelDrag()
    dispatch({ kind, occurredAt: new Date().toISOString() })
  }

  /** Rejects the extra clicks of a multi-click that straddles a finish/reset transition. */
  function acceptTransition(event: ReactMouseEvent<HTMLButtonElement>) {
    if (event.detail > 1 && event.timeStamp - lastTransitionAt.current < TRANSITION_GUARD_MS) return false
    lastTransitionAt.current = event.timeStamp
    return true
  }

  const known = <span key="known">{exercise.operands.knownAddend}</span>
  const total = <span key="total">{exercise.operands.total}</span>
  const missing = <span key="missing" className="missing-symbol">□</span>
  const plus = <span key="plus">+</span>
  const equals = <span key="equals">=</span>
  const equation = exercise.presentation === 'TOTAL_EQUALS_KNOWN_PLUS_MISSING'
    ? [total, equals, known, plus, missing]
    : exercise.presentation === 'MISSING_PLUS_KNOWN_EQUALS_TOTAL'
      ? [missing, plus, known, equals, total]
      : [known, plus, missing, equals, total]
  // aria-disabled keeps a focused control focusable when it reaches a limit.
  const removeDisabled = state.completed || state.count === 0
  const addDisabled = state.completed || state.count === MAX_BLOCKS

  return (
    <section className="activity" aria-labelledby="page-title" data-completed={state.completed}>
      <header className="activity-header">
        <a className="brand" href="#page-title" aria-label="Faro, inicio"><span aria-hidden="true">✦</span> faro</a>
        <span className="activity-label">JUGÁ CON BLOQUES</span>
      </header>
      <div className="intro">
        <p className="eyebrow">VAMOS A EXPLORAR</p>
        <h1 id="page-title">¿Cuántos bloques faltan?</h1>
        <p>Mové los bloques. Podés cambiar de idea.</p>
      </div>
      {guidance}
      <div className="equation" role="group" aria-label="Completá la igualdad" data-testid="equation">{equation}</div>
      <div className="workspace">
        <section className="known-panel" aria-labelledby="known-title">
          <h2 id="known-title">Ya tenemos <span>{exercise.operands.knownAddend}</span></h2>
          <div className="known-blocks" role="img" aria-label={`${exercise.operands.knownAddend} bloques fijos`}>
            {Array.from({ length: exercise.operands.knownAddend }, (_, index) => <span className="block known-block" key={index} />)}
          </div>
          <p className="panel-hint">Estos se quedan aquí.</p>
        </section>
        <span className="workspace-plus" aria-hidden="true">+</span>
        <section className="answer-panel" aria-labelledby="answer-title">
          <h2 id="answer-title">Vos agregás <span aria-live="polite" aria-atomic="true">{state.count}</span></h2>
          <div ref={answerArea} className={`answer-area drop-area${drag?.move === 'placeBlock' ? ' drop-ready' : ''}`} data-testid="answer-area" role="group" aria-label="Área de respuesta">
            {Array.from({ length: state.count }, (_, index) => (
              <span key={index} className="block movable-block" role="img" aria-label="Bloque para devolver" data-testid="answer-block"
                {...pieceHandlers('removeBlock')} />
            ))}
            {state.count === 0 && <span className="empty-hint" aria-hidden="true">□<small>Traé bloques aquí</small></span>}
          </div>
          <div className="quantity-controls" role="group" aria-label="Cambiar cantidad de bloques">
            <button type="button" className="quantity-button" aria-label="Quitar un bloque" aria-disabled={removeDisabled} onClick={() => { if (!removeDisabled) move('removeBlock') }}>−</button>
            <span className="control-hint">Quitá o agregá</span>
            <button type="button" className="quantity-button" aria-label="Agregar un bloque" aria-disabled={addDisabled} onClick={() => { if (!addDisabled) move('placeBlock') }}>+</button>
          </div>
        </section>
      </div>
      <section className="reserve-panel" aria-labelledby="reserve-title">
        <div className="reserve-heading"><h2 id="reserve-title">Tus bloques</h2><p><span aria-hidden="true">↔</span> Llevá y traé</p></div>
        <div ref={reserveArea} className={`reserve-area drop-area${drag?.move === 'removeBlock' ? ' drop-ready' : ''}`} data-testid="reserve-area" role="group" aria-label="Reserva de bloques">
          {Array.from({ length: MAX_BLOCKS - state.count }, (_, index) => (
            <span key={index} className="block movable-block" role="img" aria-label="Bloque para agregar" data-testid="reserve-block"
              {...pieceHandlers('placeBlock')} />
          ))}
        </div>
      </section>
      <footer className="activity-footer">
        <p className="completion-message" role="status">{state.completed ? 'Terminaste' : 'A tu ritmo.'}</p>
        {/* Both branches reuse one <button> node, so focus stays on it across finish/reset. */}
        {state.completed
          ? <button type="button" className="finish-button" ref={resetButton} onKeyDown={preventRepeatedActivation} onClick={event => {
            if (!acceptTransition(event)) return
            cancelDrag()
            dispatch({ kind: 'reset' })
            onReset()
          }}>Intentar de nuevo <span aria-hidden="true">↻</span></button>
          : <button type="button" className="finish-button" onKeyDown={preventRepeatedActivation} onClick={event => {
            if (acceptTransition(event)) move('finishAttempt')
          }}>Listo <span aria-hidden="true">→</span></button>}
      </footer>
      <p className="privacy-note">Demo local · La explicación de voz después de Listo es opcional y requiere habilitación adulta. Los movimientos se borran al reiniciar, cambiar de actividad o cerrar.</p>
      {drag && <span ref={preview} className="block drag-preview" aria-hidden="true" style={{ left: drag.x, top: drag.y }} />}
    </section>
  )
}
