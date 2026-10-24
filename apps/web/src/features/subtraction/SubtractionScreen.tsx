// SPDX-License-Identifier: AGPL-3.0-or-later
import { useReducer, useRef } from 'react'
import type { ActionTracePayloadV2 } from '../../contracts/action-v2.ts'
import type { SubtractionExercise } from '../../contracts/exercise.ts'
import { preventRepeatedActivation, useCompletionNotice } from '../shared/completion.ts'
import { usePieceDrag } from '../shared/piece-drag.ts'
import { canOperate, emptySubtractionAttempt, subtractionAttemptReducer } from './attempt.ts'
import type { Operation, PieceMove } from './attempt.ts'
import { useExerciseGuidance } from '../guidance/SpokenGuidance.tsx'

interface Props {
  exercise: SubtractionExercise
  onComplete: (trace: ActionTracePayloadV2) => void
  onReset: () => void
}

/** Mount with the exercise ID as key when changing exercises. */
export function SubtractionScreen({ exercise, onComplete, onReset }: Props) {
  const [state, dispatch] = useReducer(subtractionAttemptReducer, exercise.operands.minuend, emptySubtractionAttempt)
  const { guidance, restartGuidance } = useExerciseGuidance(exercise, state.completed)
  const availableArea = useRef<HTMLDivElement>(null)
  const removedArea = useRef<HTMLDivElement>(null)
  const resetButton = useRef<HTMLButtonElement>(null)
  useCompletionNotice(state.completed, state.trace, onComplete, resetButton)

  const { drag, preview, cancelDrag, pieceHandlers } = usePieceDrag<PieceMove>({
    canStart: move => canOperate(state, move),
    dropTarget: move => move.kind === 'removeBlock' ? removedArea.current : availableArea.current,
    onDrop: operate,
  })

  function operate(operation: Operation) {
    cancelDrag()
    dispatch({ ...operation, occurredAt: new Date().toISOString() })
  }
  function control(label: string, operation: Operation) {
    const disabled = !canOperate(state, operation)
    return <button type="button" aria-disabled={disabled} onKeyDown={preventRepeatedActivation}
      onClick={() => { if (!disabled) operate(operation) }}>{label}</button>
  }
  function tray(removed: boolean) {
    const tens = removed ? state.removedTens : state.tens
    const ones = removed ? state.removedOnes : state.ones
    const kind = removed ? 'placeBlock' : 'removeBlock'
    const title = removed ? 'Quitaste' : 'Quedan'
    const prefix = removed ? 'removed' : 'available'
    return <section className="subtraction-tray" aria-labelledby={`${prefix}-title`}>
      <h2 id={`${prefix}-title`}>{title} <span data-testid={`${prefix}-total`}>{tens * 10 + ones}</span></h2>
      <p data-testid={`${prefix}-counts`}>{tens} decenas y {ones} unidades</p>
      <div ref={removed ? removedArea : availableArea} className={`piece-area drop-area${drag && (removed === (drag.move.kind === 'removeBlock')) ? ' drop-ready' : ''}`}
        role="group" aria-label={`Piezas: ${title.toLowerCase()}`} data-testid={`${prefix}-area`}>
        {([10, 1] as const).map(value => Array.from({ length: value === 10 ? tens : ones }, (_, index) =>
          <span key={`${value}-${index}`} className={`math-piece ${value === 10 ? 'ten-piece' : 'unit-piece'}`}
            role="img" aria-label={`${value === 10 ? 'Decena' : 'Unidad'} para ${removed ? 'devolver' : 'quitar'}`}
            data-testid={`${prefix}-${value === 10 ? 'ten' : 'one'}`}
            {...pieceHandlers({ kind, value })}>
            {Array.from({ length: value === 10 ? 10 : 1 }, (_, cell) => <span aria-hidden="true" key={cell} />)}
          </span>,
        ))}
        {tens + ones === 0 && <p className="tray-empty">Todavía no hay piezas aquí.</p>}
      </div>
      <div className="piece-controls" role="group" aria-label={removed ? 'Devolver piezas' : 'Quitar piezas'}>
        {control(`${removed ? 'Devolver' : 'Quitar'} 1 decena`, { kind, value: 10 })}
        {control(`${removed ? 'Devolver' : 'Quitar'} 1 unidad`, { kind, value: 1 })}
      </div>
    </section>
  }
  return <section className="activity subtraction-activity" aria-labelledby="page-title" data-completed={state.completed}>
    <header className="activity-header">
      <a className="brand" href="#page-title" aria-label="Faro, inicio"><span aria-hidden="true">✦</span> faro</a>
      <span className="activity-label">EXPLORÁ LA RESTA</span>
    </header>
    <div className="intro">
      <p className="eyebrow">VAMOS A EXPLORAR</p>
      <h1 id="page-title">¿Cuántos quedan?</h1>
      <p>Mové decenas y unidades. Podés cambiar de idea.</p>
    </div>
    {guidance}
    <div className="equation subtraction-equation" role="group" aria-label="Explorá la resta" data-testid="subtraction-equation">
      <span>{exercise.operands.minuend}</span><span>−</span><span>{exercise.operands.subtrahend}</span><span>=</span>
      <span className="missing-symbol" role="img" aria-label="Cantidad por descubrir">□</span>
    </div>
    <div className="subtraction-trays">{tray(false)}{tray(true)}</div>
    <div className="exchange-controls" role="group" aria-label="Cambiar piezas que quedan">
      {control('Cambiar 1 decena por 10 unidades', { kind: 'decomposeTen' })}
      {control('Cambiar 10 unidades por 1 decena', { kind: 'composeTen' })}
    </div>
    <p className="subtraction-status" role="status" aria-atomic="true">{state.completed ? 'Terminaste'
      : `Quedan ${state.tens} decenas y ${state.ones} unidades: ${state.tens * 10 + state.ones}. Quitaste ${state.removedTens * 10 + state.removedOnes}.`}</p>
    <footer className="activity-footer sequence-footer">
      <button type="button" ref={resetButton} className="restart-button" aria-disabled={state.trace.events.length === 0}
        onKeyDown={preventRepeatedActivation} onClick={() => {
          if (state.trace.events.length === 0) return
          cancelDrag()
          dispatch({ kind: 'reset' })
          restartGuidance()
          onReset()
        }}>Empezar de nuevo <span aria-hidden="true">↻</span></button>
      <button type="button" className="finish-button" aria-disabled={state.completed} onKeyDown={preventRepeatedActivation}
        onMouseDown={event => { if (state.completed) event.preventDefault() }}
        onClick={() => { if (!state.completed) operate({ kind: 'finishAttempt' }) }}>Listo <span aria-hidden="true">→</span></button>
    </footer>
    <p className="privacy-note">Demo local · La explicación de voz después de Listo es opcional y requiere habilitación adulta. Los movimientos se borran al reiniciar, cambiar de actividad o cerrar.</p>
    {drag && <span ref={preview} className={`math-piece drag-preview ${drag.move.value === 10 ? 'ten-piece' : 'unit-piece'}`}
      aria-hidden="true" style={{ left: drag.x, top: drag.y }}>
      {Array.from({ length: drag.move.value === 10 ? 10 : 1 }, (_, index) => <span key={index} />)}
    </span>}
  </section>
}
