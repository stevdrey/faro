// SPDX-License-Identifier: AGPL-3.0-or-later
import { useCallback, useState } from 'react'
import { AudioSessionProvider } from './features/recording/AudioSession.tsx'
import { ExplanationRecorder } from './features/recording/ExplanationRecorder.tsx'
import { MissingAddendScreen } from './features/missing-addend/MissingAddendScreen.tsx'
import { SequenceScreen } from './features/sequences/SequenceScreen.tsx'
import { SubtractionScreen } from './features/subtraction/SubtractionScreen.tsx'
import { exerciseFor, initialSelection, isExample } from './features/shared/activity-selection.ts'
import type { Activity, ActivitySelection } from './features/shared/activity-selection.ts'
import type { CompletedTrace } from './features/shared/attempt-completion.ts'
import { useAttemptCompletion } from './features/shared/use-attempt-completion.ts'
import type { AttemptCompletionController } from './features/shared/use-attempt-completion.ts'

export function App() {
  const completion = useAttemptCompletion(exerciseFor(initialSelection).exerciseId)
  return <DemoActivities completion={completion} />
}

/** Activity selection and completion selection change together, without resetting an active choice. */
export function DemoActivities({ completion }: { completion: AttemptCompletionController }) {
  return <AudioSessionProvider><Activities completion={completion} /></AudioSessionProvider>
}

function Activities({ completion }: { completion: AttemptCompletionController }) {
  const [authorized, setAuthorized] = useState(false)
  const [selection, setSelection] = useState<ActivitySelection>(initialSelection)
  const exercise = exerciseFor(selection)
  const { complete, reset } = completion
  // Each screen reports the exercise it shows, not whatever the controller last selected.
  const onComplete = useCallback((trace: CompletedTrace) => complete(exercise.exerciseId, trace), [complete, exercise.exerciseId])
  const onReset = useCallback(() => reset(exercise.exerciseId), [reset, exercise.exerciseId])

  function select(next: ActivitySelection) {
    completion.selectExercise(exerciseFor(next).exerciseId)
    setSelection(next)
  }

  const activityButton = (activity: Activity, label: string) =>
    <button type="button" aria-pressed={selection.activity === activity}
      onClick={() => select({ ...selection, activity })}>{label}</button>

  return <>
    <nav className="activity-picker" aria-label="Elegir actividad">
      {activityButton('blocks', 'Bloques')}
      {activityButton('sequences', 'Secuencias')}
      {activityButton('subtraction', 'Restas')}
    </nav>
    {exercise.type === 'MISSING_ADDEND'
      ? <MissingAddendScreen exercise={exercise} onComplete={onComplete} onReset={onReset} />
      : exercise.type === 'SUBTRACTION'
        ? <SubtractionScreen exercise={exercise} onComplete={onComplete} onReset={onReset} />
      : <>
        <div className="example-picker"><label>Elegí una secuencia <select value={selection.example} onChange={event => {
          const example = event.target.value
          if (isExample(example)) select({ ...selection, example })
        }}>
          <option value="descending">30, 28, □</option>
          <option value="ascending">10, 12, □</option>
        </select></label><p>Cambiar de ejemplo borra tus saltos.</p></div>
        <SequenceScreen key={exercise.exerciseId} exercise={exercise} onComplete={onComplete} onReset={onReset} />
      </>}
    {completion.state.completedAttempt && <ExplanationRecorder key={exercise.exerciseId}
      authorized={authorized} authorize={() => setAuthorized(true)} />}
  </>
}
