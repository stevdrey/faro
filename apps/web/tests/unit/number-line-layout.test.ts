// SPDX-License-Identifier: AGPL-3.0-or-later
import assert from 'node:assert/strict'
import test from 'node:test'
import { emptySequenceAttempt, INT32_MAX, INT32_MIN, sequenceAttemptReducer, visibleJumps } from '../../src/features/sequences/attempt.ts'
import type { JumpKind, JumpSize, SequenceAttempt } from '../../src/features/sequences/attempt.ts'
import { MAX_WIDTH, MIN_WIDTH, niceStep, numberLineLayout } from '../../src/features/sequences/layout.ts'

const occurredAt = '2026-01-01T00:00:00.000Z'
type Move = readonly [JumpKind, JumpSize]

function layoutAfter(terms: readonly number[], moves: readonly Move[]) {
  let state: SequenceAttempt = emptySequenceAttempt(terms.at(-1)!)
  for (const [kind, value] of moves) state = sequenceAttemptReducer(state, { kind, value, occurredAt })
  return numberLineLayout(terms, state.position, visibleJumps(state.trace))
}

const isNiceStep = (step: number) => [1, 2, 5].some(factor => Number.isInteger(Math.log10(step / factor)))

test('tick steps are always 1, 2 or 5 × 10ⁿ', () => {
  for (const spacing of [1, 27, 39, 100, 5000]) {
    for (const pxPerUnit of [0.0001, 0.37, 1, 28, 94]) assert.ok(isNiceStep(niceStep(spacing, pxPerUnit)))
  }
})

test('every term and visited position is labeled after exploring past zero', () => {
  const layout = layoutAfter([30, 28], Array(4).fill(['jumpLeft', 10]))
  for (const value of [30, 28, 18, 8, -2, -12]) assert.ok(layout.ticks.includes(value), `${value} is labeled`)
  assert.deepEqual(layout.markers, [])
})

test('the line widens with the explored range until the cap, keeping round ticks', () => {
  const start = layoutAfter([30, 28], [])
  const explored = layoutAfter([30, 28], Array(10).fill(['jumpRight', 10]))
  assert.equal(start.width, MIN_WIDTH)
  assert.ok(explored.width > start.width)
  assert.ok(explored.x(29) - explored.x(28) >= 28)
  const extreme = numberLineLayout([INT32_MIN, INT32_MAX], 0, [])
  assert.equal(extreme.width, MAX_WIDTH)
  const step = extreme.ticks[1] - extreme.ticks[0]
  assert.ok(isNiceStep(step) && extreme.ticks.length < 60)
  assert.ok(extreme.markers.includes(INT32_MIN) && extreme.markers.includes(INT32_MAX))
})

test('a reversal over the same segment gets its own lane while separate arcs share one', () => {
  const reversal = layoutAfter([30, 28], [['jumpLeft', 10], ['jumpRight', 10]])
  assert.deepEqual(reversal.arcs.map(arc => arc.lane), [0, 1])
  const onward = layoutAfter([30, 28], [['jumpRight', 10], ['jumpRight', 10], ['jumpRight', 10]])
  assert.deepEqual(onward.arcs.map(arc => arc.lane), [0, 0, 0])
  assert.equal(onward.axisY, 100)
})

test('arc labels stay inside the drawing even for late steps at the line ends', () => {
  const moves: Move[] = [...Array(60).fill(['jumpLeft', 1]), ...Array(60).fill(['jumpRight', 1])]
  const layout = layoutAfter([10, 12], moves)
  for (const arc of layout.arcs) {
    const half = arc.label.length * 4.5
    assert.ok(arc.labelX - half >= 0 && arc.labelX + half <= layout.width, `step ${arc.jump.event.step} fits`)
  }
})
