// SPDX-License-Identifier: AGPL-3.0-or-later
import { useEffect, useRef } from 'react'
import type { KeyboardEvent, RefObject } from 'react'
import type { ActionTracePayload } from '../../contracts/action.ts'

/** Delivers each completed trace once, even under StrictMode, then moves focus to the reset control. */
export function useCompletionNotice(
  completed: boolean,
  trace: ActionTracePayload,
  onComplete: (trace: ActionTracePayload) => void,
  focusTarget: RefObject<HTMLElement | null>,
) {
  const notifiedTrace = useRef<ActionTracePayload | null>(null)
  useEffect(() => {
    if (!completed) notifiedTrace.current = null
    if (completed && notifiedTrace.current !== trace) {
      notifiedTrace.current = trace
      onComplete(trace)
      focusTarget.current?.focus()
    }
  }, [completed, trace, onComplete, focusTarget])
}

export function preventRepeatedActivation(event: KeyboardEvent<HTMLButtonElement>) {
  // A held key is still one action, even after the finish/reset transition.
  if (event.repeat && (event.key === 'Enter' || event.key === ' ')) {
    event.preventDefault()
  }
}
