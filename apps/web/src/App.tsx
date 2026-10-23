// SPDX-License-Identifier: AGPL-3.0-or-later
import { useState } from 'react'
import { demoExercise } from './features/missing-addend/exercise.ts'
import { MissingAddendScreen } from './features/missing-addend/MissingAddendScreen.tsx'
import { ascendingExercise, descendingExercise } from './features/sequences/exercise.ts'
import { SequenceScreen } from './features/sequences/SequenceScreen.tsx'
import { subtractionExercise } from './features/subtraction/exercise.ts'
import { SubtractionScreen } from './features/subtraction/SubtractionScreen.tsx'
import { useAttemptCompletion } from './features/shared/use-attempt-completion.ts'
import type { AttemptCompletionController } from './features/shared/use-attempt-completion.ts'

type Activity = 'blocks' | 'sequences' | 'subtraction'
type Example = 'descending' | 'ascending'

export function App() {
  const completion = useAttemptCompletion(demoExercise.exerciseId)
  return <DemoActivities completion={completion} />
}

/** Activity selection and completion selection change together, without resetting an active choice. */
export function DemoActivities({ completion }: { completion: AttemptCompletionController }) {
  const [activity, setActivity] = useState<Activity>('blocks')
  const [example, setExample] = useState<Example>('descending')
  const sequence = example === 'descending' ? descendingExercise : ascendingExercise

  function selectActivity(next: Activity) {
    completion.selectExercise(next === 'blocks' ? demoExercise.exerciseId
      : next === 'subtraction' ? subtractionExercise.exerciseId : sequence.exerciseId)
    setActivity(next)
  }

  function selectExample(next: Example) {
    completion.selectExercise(next === 'descending' ? descendingExercise.exerciseId : ascendingExercise.exerciseId)
    setExample(next)
  }

  return <>
    <nav className="activity-picker" aria-label="Elegir actividad">
      <button type="button" aria-pressed={activity === 'blocks'} onClick={() => selectActivity('blocks')}>Bloques</button>
      <button type="button" aria-pressed={activity === 'sequences'} onClick={() => selectActivity('sequences')}>Secuencias</button>
      <button type="button" aria-pressed={activity === 'subtraction'} onClick={() => selectActivity('subtraction')}>Restas</button>
    </nav>
    {activity === 'blocks'
      ? <MissingAddendScreen exercise={demoExercise} onComplete={completion.onComplete} onReset={completion.onReset} />
      : activity === 'subtraction'
        ? <SubtractionScreen exercise={subtractionExercise} onComplete={completion.onComplete} onReset={completion.onReset} />
      : <>
        <div className="example-picker"><label>Elegí una secuencia <select value={example} onChange={event => selectExample(event.target.value as Example)}>
          <option value="descending">30, 28, □</option>
          <option value="ascending">10, 12, □</option>
        </select></label><p>Cambiar de ejemplo borra tus saltos.</p></div>
        <SequenceScreen key={sequence.exerciseId} exercise={sequence} onComplete={completion.onComplete} onReset={completion.onReset} />
      </>}
  </>
}
