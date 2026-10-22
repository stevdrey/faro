// SPDX-License-Identifier: AGPL-3.0-or-later
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { demoExercise } from '../../src/features/missing-addend/exercise.ts'
import { MissingAddendScreen } from '../../src/features/missing-addend/MissingAddendScreen.tsx'
import type { MissingAddendExercise } from '../../src/contracts/exercise.ts'
import type { ActionTracePayload } from '../../src/contracts/action.ts'
import '../../src/styles.css'

export interface TestEvidence {
  completions: ActionTracePayload[]
  frozen: boolean
  resets: number
}

declare global { interface Window { screenEvidence: TestEvidence } }
window.screenEvidence = { completions: [], frozen: false, resets: 0 }
const presentation = new URLSearchParams(location.search).get('presentation')
const exercise: MissingAddendExercise = presentation === 'TOTAL_EQUALS_KNOWN_PLUS_MISSING' || presentation === 'MISSING_PLUS_KNOWN_EQUALS_TOTAL'
  ? { ...demoExercise, presentation } : demoExercise
createRoot(document.getElementById('app')!).render(
  <StrictMode>
    <MissingAddendScreen exercise={exercise} onComplete={trace => {
      window.screenEvidence.completions.push(trace)
      window.screenEvidence.frozen = Object.isFrozen(trace) && Object.isFrozen(trace.events) && trace.events.every(Object.isFrozen)
    }} onReset={() => { window.screenEvidence.resets++ }} />
  </StrictMode>,
)
