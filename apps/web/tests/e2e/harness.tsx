// SPDX-License-Identifier: AGPL-3.0-or-later
import { StrictMode, useEffect } from 'react'
import { createRoot } from 'react-dom/client'
import { demoExercise } from '../../src/features/missing-addend/exercise.ts'
import { MissingAddendScreen } from '../../src/features/missing-addend/MissingAddendScreen.tsx'
import { SequenceScreen } from '../../src/features/sequences/SequenceScreen.tsx'
import { ascendingExercise, descendingExercise } from '../../src/features/sequences/exercise.ts'
import type { MissingAddendExercise } from '../../src/contracts/exercise.ts'
import type { ActionTracePayloadV2 } from '../../src/contracts/action-v2.ts'
import { SubtractionScreen } from '../../src/features/subtraction/SubtractionScreen.tsx'
import { subtractionExercise } from '../../src/features/subtraction/exercise.ts'
import type { ActionTracePayload } from '../../src/contracts/action.ts'
import { DemoActivities } from '../../src/App.tsx'
import { useAttemptCompletion } from '../../src/features/shared/use-attempt-completion.ts'
import type { CompletedAttempt } from '../../src/features/shared/attempt-completion.ts'
import '../../src/styles.css'

export interface TestEvidence {
  completions: (ActionTracePayload | ActionTracePayloadV2)[]
  frozen: boolean
  resets: number
}

interface AttemptEvidence {
  current: CompletedAttempt | null
  deliveries: CompletedAttempt[]
}

declare global {
  interface Window {
    screenEvidence: TestEvidence
    attemptEvidence: AttemptEvidence
    redeliverCompletion: () => void
  }
}
window.screenEvidence = { completions: [], frozen: false, resets: 0 }
window.attemptEvidence = { current: null, deliveries: [] }

function AppHarness() {
  const completion = useAttemptCompletion(demoExercise.exerciseId)
  useEffect(() => {
    const attempt = completion.state.completedAttempt
    window.attemptEvidence.current = attempt
    if (attempt && window.attemptEvidence.deliveries.at(-1) !== attempt) {
      window.attemptEvidence.deliveries.push(attempt)
    }
    window.redeliverCompletion = () => { if (attempt) completion.onComplete(attempt.trace) }
  }, [completion.state, completion.onComplete])
  return <DemoActivities completion={completion} />
}
const parameters = new URLSearchParams(location.search)
const presentation = parameters.get('presentation')
const exercise: MissingAddendExercise = presentation === 'TOTAL_EQUALS_KNOWN_PLUS_MISSING' || presentation === 'MISSING_PLUS_KNOWN_EQUALS_TOTAL'
  ? { ...demoExercise, presentation } : demoExercise
function onComplete(trace: ActionTracePayload | ActionTracePayloadV2) {
  window.screenEvidence.completions.push(trace)
  window.screenEvidence.frozen = Object.isFrozen(trace) && Object.isFrozen(trace.events) && trace.events.every(Object.isFrozen)
}
function onReset() { window.screenEvidence.resets++ }
createRoot(document.getElementById('app')!).render(<StrictMode>
  {parameters.get('activity') === 'app' ? <AppHarness /> : parameters.get('activity') === 'sequences'
    ? <SequenceScreen exercise={parameters.get('example') === 'ascending' ? ascendingExercise : descendingExercise} onComplete={onComplete} onReset={onReset} />
    : parameters.get('activity') === 'subtraction'
      ? <SubtractionScreen exercise={subtractionExercise} onComplete={onComplete} onReset={onReset} />
    : <MissingAddendScreen exercise={exercise} onComplete={onComplete} onReset={onReset} />}
</StrictMode>)
