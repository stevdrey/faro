// SPDX-License-Identifier: AGPL-3.0-or-later
import { useEffect, useRef } from 'react'
import type { VisibleJump } from './attempt.ts'

interface Props {
  terms: readonly number[]
  position: number
  jumps: readonly VisibleJump[]
}

export function NumberLine({ terms, position, jumps }: Props) {
  const viewport = useRef<HTMLDivElement>(null)
  const positions = [...terms, position, ...jumps.flatMap(jump => [jump.from, jump.to])]
  const minimum = Math.min(...positions) - 2
  const maximum = Math.max(...positions) + 2
  const width = 640
  const axisY = Math.max(100, 40 + jumps.length * 32)
  const x = (value: number) => 36 + (value - minimum) / (maximum - minimum) * (width - 72)
  const tickSize = Math.max(1, Math.ceil((maximum - minimum) / 12))
  const ticks: number[] = []
  for (let tick = Math.ceil(minimum / tickSize) * tickSize; tick <= maximum; tick += tickSize) ticks.push(tick)
  const currentX = x(position)

  useEffect(() => {
    const element = viewport.current
    if (element) element.scrollLeft = Math.max(0, currentX - element.clientWidth / 2)
  }, [currentX])

  return (
    <div ref={viewport} className="number-line-scroll" role="region" aria-label="Recta numérica; podés desplazarla de lado a lado" tabIndex={0}>
      <svg className="number-line" viewBox={`0 0 ${width} ${axisY + 76}`} width={width} height={axisY + 76}
        role="img" aria-label={`Recta numérica. Posición actual: ${position}. ${jumps.length} saltos; detalles debajo.`}>
        <line x1="16" x2={width - 16} y1={axisY} y2={axisY} className="number-axis" />
        {ticks.map(tick => <g key={tick} aria-hidden="true">
          <line x1={x(tick)} x2={x(tick)} y1={axisY - 5} y2={axisY + 6} className="number-axis" />
          <text x={x(tick)} y={axisY + 26} textAnchor="middle">{tick}</text>
        </g>)}
        {jumps.map(({ from, to, event }, index) => {
          const height = (index + 1) * 32
          const middle = (x(from) + x(to)) / 2
          return <g key={event.step} className={event.kind === 'jumpLeft' ? 'jump-left' : 'jump-right'} aria-hidden="true" data-testid="jump-arc">
            <path d={`M ${x(from)} ${axisY - 9} Q ${middle} ${axisY - height * 2 - 9} ${x(to)} ${axisY - 9}`} />
            <path d={`M ${x(to) - 4} ${axisY - 16} L ${x(to)} ${axisY - 9} L ${x(to) + 4} ${axisY - 16}`} />
            <text x={middle} y={axisY - height - 16} textAnchor="middle">{event.step}: {event.kind === 'jumpLeft' ? '←' : '→'} {event.value}</text>
          </g>
        })}
        <circle cx={currentX} cy={axisY} r="8" className="current-position" />
        <text x={Math.max(110, Math.min(width - 110, currentX))} y={axisY + 57} textAnchor="middle" className="position-label">Estás en {position}</text>
      </svg>
    </div>
  )
}
