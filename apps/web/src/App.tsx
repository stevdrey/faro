// SPDX-License-Identifier: AGPL-3.0-or-later
import { useState } from 'react'
import { demoExercise } from './features/missing-addend/exercise.ts'
import { MissingAddendScreen } from './features/missing-addend/MissingAddendScreen.tsx'
import { ascendingExercise, descendingExercise } from './features/sequences/exercise.ts'
import { SequenceScreen } from './features/sequences/SequenceScreen.tsx'
import { subtractionExercise } from './features/subtraction/exercise.ts'
import { SubtractionScreen } from './features/subtraction/SubtractionScreen.tsx'

// Local integration seams for FARO-008. Nothing is posted, logged or persisted.
const ignoreCompletion = () => {}
const ignoreReset = () => {}

export function App() {
  const [activity, setActivity] = useState('blocks')
  const [example, setExample] = useState('descending')
  const sequence = example === 'descending' ? descendingExercise : ascendingExercise
  return <>
    <nav className="activity-picker" aria-label="Elegir actividad">
      <button type="button" aria-pressed={activity === 'blocks'} onClick={() => setActivity('blocks')}>Bloques</button>
      <button type="button" aria-pressed={activity === 'sequences'} onClick={() => setActivity('sequences')}>Secuencias</button>
      <button type="button" aria-pressed={activity === 'subtraction'} onClick={() => setActivity('subtraction')}>Restas</button>
    </nav>
    {activity === 'blocks'
      ? <MissingAddendScreen exercise={demoExercise} onComplete={ignoreCompletion} onReset={ignoreReset} />
      : activity === 'subtraction'
        ? <SubtractionScreen exercise={subtractionExercise} onComplete={ignoreCompletion} onReset={ignoreReset} />
      : <>
        <div className="example-picker"><label>Elegí una secuencia <select value={example} onChange={event => setExample(event.target.value)}>
          <option value="descending">30, 28, □</option>
          <option value="ascending">10, 12, □</option>
        </select></label><p>Cambiar de ejemplo borra tus saltos.</p></div>
        <SequenceScreen key={sequence.exerciseId} exercise={sequence} onComplete={ignoreCompletion} onReset={ignoreReset} />
      </>}
  </>
}
