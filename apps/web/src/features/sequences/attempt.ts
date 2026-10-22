// SPDX-License-Identifier: AGPL-3.0-or-later
import type { ActionTracePayload, JumpLeft, JumpRight } from '../../contracts/action.ts'

/** Signed 32-bit bounds shared with v1 trace replay. */
export const INT32_MIN = -2147483648
export const INT32_MAX = 2147483647
export const JUMP_SIZES = [1, 2, 5, 10] as const
export type JumpSize = typeof JUMP_SIZES[number]
export type JumpKind = 'jumpLeft' | 'jumpRight'

export interface SequenceAttempt {
  readonly position: number
  readonly completed: boolean
  readonly trace: ActionTracePayload
}

export type SequenceAction =
  | { readonly kind: JumpKind; readonly value: JumpSize; readonly occurredAt: string }
  | { readonly kind: 'finishAttempt'; readonly occurredAt: string }
  | { readonly kind: 'reset' }

export function isInt32(value: unknown): value is number {
  return Number.isInteger(value) && (value as number) >= INT32_MIN && (value as number) <= INT32_MAX
}

export function jumpDelta(kind: JumpKind, value: number): number {
  return kind === 'jumpLeft' ? -value : value
}

export function emptySequenceAttempt(initialValue: number): SequenceAttempt {
  if (!isInt32(initialValue)) throw new RangeError('Initial position must be a signed 32-bit integer')
  return Object.freeze({
    position: initialValue, completed: false,
    trace: Object.freeze({ schemaVersion: 1, initialValue, events: Object.freeze([]) }),
  })
}

export function canJump(position: number, kind: JumpKind, value: JumpSize): boolean {
  return JUMP_SIZES.includes(value) && isInt32(position + jumpDelta(kind, value))
}

/** Records accepted semantic moves only. Selecting a size is not a jump. */
export function sequenceAttemptReducer(state: SequenceAttempt, action: SequenceAction): SequenceAttempt {
  if (action.kind === 'reset') return emptySequenceAttempt(state.trace.initialValue)
  if (state.completed) return state
  if (action.kind !== 'finishAttempt' && !canJump(state.position, action.kind, action.value)) return state
  const metadata = { step: state.trace.events.length + 1, occurredAt: action.occurredAt }
  const event = Object.freeze(action.kind === 'finishAttempt'
    ? { ...metadata, kind: action.kind }
    : { ...metadata, kind: action.kind, value: action.value })
  return Object.freeze({
    position: state.position + (action.kind === 'finishAttempt' ? 0 : jumpDelta(action.kind, action.value)),
    completed: action.kind === 'finishAttempt',
    trace: Object.freeze({ ...state.trace, events: Object.freeze([...state.trace.events, event]) }),
  })
}

export interface VisibleJump {
  readonly from: number
  readonly to: number
  readonly event: JumpLeft | JumpRight
}

/** Derive the drawing/history from the same trace delivered on completion. */
export function visibleJumps(trace: ActionTracePayload): readonly VisibleJump[] {
  let position = trace.initialValue
  const jumps: VisibleJump[] = []
  for (const event of trace.events) {
    if (event.kind !== 'jumpLeft' && event.kind !== 'jumpRight') continue
    const to = position + jumpDelta(event.kind, event.value)
    jumps.push({ from: position, to, event })
    position = to
  }
  return jumps
}
