// SPDX-License-Identifier: AGPL-3.0-or-later
import type { VisibleJump } from './attempt.ts'

export const MIN_WIDTH = 640
/** Beyond this width the scale compresses instead of growing further. */
export const MAX_WIDTH = 6000
const MARGIN = 36
const MIN_UNIT_PX = 28
const LANE_HEIGHT = 32
/** Approximate advance of one 16px system-ui character; labels are estimated, not measured. */
const CHAR_PX = 9
const LABEL_GAP = 12

export interface ArcLayout {
  readonly jump: VisibleJump
  readonly lane: number
  readonly fromX: number
  readonly toX: number
  /** Peak height of the arc above the axis. */
  readonly height: number
  readonly label: string
  readonly labelX: number
}

export interface NumberLineLayout {
  readonly width: number
  readonly axisY: number
  readonly x: (value: number) => number
  /** Labeled ticks on a 1, 2 or 5 × 10ⁿ step. */
  readonly ticks: readonly number[]
  /** Terms and visited positions that fall between labeled ticks. */
  readonly markers: readonly number[]
  readonly arcs: readonly ArcLayout[]
}

const labelWidth = (text: string) => text.length * CHAR_PX

export function arcLabel({ event }: VisibleJump): string {
  return `${event.step}: ${event.kind === 'jumpLeft' ? '←' : '→'} ${event.value}`
}

/** Smallest 1, 2 or 5 × 10ⁿ step whose spacing fits the widest label. */
export function niceStep(minimumSpacing: number, pxPerUnit: number): number {
  for (let magnitude = 1; ; magnitude *= 10) {
    for (const factor of [1, 2, 5]) {
      if (factor * magnitude * pxPerUnit >= minimumSpacing) return factor * magnitude
    }
  }
}

export function numberLineLayout(terms: readonly number[], position: number, jumps: readonly VisibleJump[]): NumberLineLayout {
  const visited = [...terms, position, ...jumps.flatMap(jump => [jump.from, jump.to])]
  let low = position
  let high = position
  for (const value of visited) {
    low = Math.min(low, value)
    high = Math.max(high, value)
  }
  const minimum = low - 2
  const maximum = high + 2
  const range = maximum - minimum
  const tickSpacing = Math.max(labelWidth(String(minimum)), labelWidth(String(maximum))) + LABEL_GAP
  // Grow the line so each unit can carry its own label, until the width cap.
  const width = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, range * Math.max(MIN_UNIT_PX, tickSpacing) + MARGIN * 2))
  const pxPerUnit = (width - MARGIN * 2) / range
  const x = (value: number) => MARGIN + (value - minimum) * pxPerUnit

  const step = niceStep(tickSpacing, pxPerUnit)
  const ticks: number[] = []
  for (let tick = Math.ceil(minimum / step) * step; tick <= maximum; tick += step) ticks.push(tick)
  const markers = [...new Set(visited)].filter(value => value % step !== 0).sort((a, b) => a - b)

  // Each arc takes the lowest lane whose arcs and labels it does not overlap.
  const lanes: Array<Array<readonly [number, number]>> = []
  const arcs = jumps.map(jump => {
    const fromX = x(jump.from)
    const toX = x(jump.to)
    const label = arcLabel(jump)
    const half = labelWidth(label) / 2
    const labelX = Math.min(width - half - 4, Math.max(half + 4, (fromX + toX) / 2))
    const extent = [Math.min(fromX, toX, labelX - half), Math.max(fromX, toX, labelX + half)] as const
    let lane = lanes.findIndex(occupied => occupied.every(([left, right]) => Math.max(left, extent[0]) >= Math.min(right, extent[1])))
    if (lane === -1) lane = lanes.push([]) - 1
    lanes[lane].push(extent)
    return { jump, lane, fromX, toX, height: (lane + 1) * LANE_HEIGHT, label, labelX }
  })
  const axisY = Math.max(100, 40 + lanes.length * LANE_HEIGHT)
  return { width, axisY, x, ticks, markers, arcs }
}
