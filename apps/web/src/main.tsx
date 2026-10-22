// SPDX-License-Identifier: AGPL-3.0-or-later
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { demoExercise } from './features/missing-addend/exercise.ts'
import { MissingAddendScreen } from './features/missing-addend/MissingAddendScreen.tsx'
import './styles.css'

// Local integration seam for FARO-008. No attempt is posted, logged or persisted.
const ignoreCompletion = () => {}
const ignoreReset = () => {}

const root = document.getElementById('app')
if (!root) throw new Error('Missing application root')
createRoot(root).render(
  <StrictMode>
    <MissingAddendScreen exercise={demoExercise} onComplete={ignoreCompletion} onReset={ignoreReset} />
  </StrictMode>,
)
