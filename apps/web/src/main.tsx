// SPDX-License-Identifier: AGPL-3.0-or-later
import { StrictMode, useState } from 'react'
import { createRoot } from 'react-dom/client'
import type { ActionTracePayload } from './contracts/action.ts'
import { demoExercise } from './features/missing-addend/exercise.ts'
import { MissingAddendScreen } from './features/missing-addend/MissingAddendScreen.tsx'
import './styles.css'

function App() {
  // Local integration seam for FARO-008. No attempt is posted, logged or persisted.
  const [, setCompletedTrace] = useState<ActionTracePayload | null>(null)
  return <MissingAddendScreen exercise={demoExercise} onComplete={setCompletedTrace} onReset={() => setCompletedTrace(null)} />
}

const root = document.getElementById('app')
if (!root) throw new Error('Missing application root')
createRoot(root).render(<StrictMode><App /></StrictMode>)
