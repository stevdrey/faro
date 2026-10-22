// SPDX-License-Identifier: AGPL-3.0-or-later
import { useEffect, useRef } from 'react'
import type { KeyboardEvent, RefObject } from 'react'
import type { ActionTracePayload } from '../../contracts/action.ts'
import type { ActionTracePayloadV2 } from '../../contracts/action-v2.ts'

/** Delivers each completed trace once, even under StrictMode, then moves focus to the reset control. */
export function useCompletionNotice<T extends ActionTracePayload | ActionTracePayloadV2>(
  completed: boolean,
  trace: T,
  onComplete: (trace: T) => void,
  focusTarget: RefObject<HTMLElement | null>,
) {
  const notifiedTrace = useRef<T | null>(null)
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
