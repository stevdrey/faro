// SPDX-License-Identifier: AGPL-3.0-or-later
import { useEffect, useRef, useState } from 'react'
import type { VisibleJump } from './attempt.ts'
import { numberLineLayout } from './layout.ts'

interface Props {
  terms: readonly number[]
  position: number
  jumps: readonly VisibleJump[]
}

export function NumberLine({ terms, position, jumps }: Props) {
  const viewport = useRef<HTMLDivElement>(null)
  const [scrollable, setScrollable] = useState(false)
  const { width, axisY, x, ticks, markers, arcs } = numberLineLayout(terms, position, jumps)
  const currentX = x(position)

  useEffect(() => {
    const element = viewport.current
    if (!element) return
    const measure = () => setScrollable(element.scrollWidth > element.clientWidth)
    measure()
    const observer = new ResizeObserver(measure)
    observer.observe(element)
    return () => observer.disconnect()
  }, [width])

  useEffect(() => {
    const element = viewport.current
    if (element) element.scrollLeft = Math.max(0, currentX - element.clientWidth / 2)
  }, [currentX])

  // Only an overflowing line is a keyboard stop that announces scrolling.
  return (
    <div ref={viewport} className="number-line-scroll" role="region" tabIndex={scrollable ? 0 : undefined}
      aria-label={scrollable ? 'Recta numérica; podés desplazarla de lado a lado' : 'Recta numérica'}>
      <svg className="number-line" viewBox={`0 0 ${width} ${axisY + 76}`} width={width} height={axisY + 76}
        role="img" aria-label={`Recta numérica. Posición actual: ${position}. ${jumps.length} saltos; detalles debajo.`}>
        <line x1="16" x2={width - 16} y1={axisY} y2={axisY} className="number-axis" />
        {ticks.map(tick => <g key={tick} aria-hidden="true">
          <line x1={x(tick)} x2={x(tick)} y1={axisY - 5} y2={axisY + 6} className="number-axis" />
          <text x={x(tick)} y={axisY + 26} textAnchor="middle">{tick}</text>
        </g>)}
        {markers.map(marker => <line key={marker} x1={x(marker)} x2={x(marker)} y1={axisY - 3} y2={axisY + 4} className="number-axis" aria-hidden="true" />)}
        {arcs.map(({ jump: { event }, fromX, toX, height, label, labelX }) => {
          const middle = (fromX + toX) / 2
          return <g key={event.step} className={event.kind === 'jumpLeft' ? 'jump-left' : 'jump-right'} aria-hidden="true" data-testid="jump-arc">
            <path d={`M ${fromX} ${axisY - 9} Q ${middle} ${axisY - height * 2 - 9} ${toX} ${axisY - 9}`} />
            <path d={`M ${toX - 4} ${axisY - 16} L ${toX} ${axisY - 9} L ${toX + 4} ${axisY - 16}`} />
            <text x={labelX} y={axisY - height - 16} textAnchor="middle">{label}</text>
          </g>
        })}
        <circle cx={currentX} cy={axisY} r="8" className="current-position" />
        <text x={Math.max(110, Math.min(width - 110, currentX))} y={axisY + 57} textAnchor="middle" className="position-label">Estás en {position}</text>
      </svg>
    </div>
  )
}
