// SPDX-License-Identifier: AGPL-3.0-or-later
import descendingFixture from '../../../../../contracts/exercises/v1/fixtures/sequence.json' with { type: 'json' }
import type { SequenceExercise } from '../../contracts/exercise.ts'
import { toSequenceExercise } from './fixture.ts'

// Trusted repository fixture, not an API response. No expected answer is sent to this screen.
export const descendingExercise = toSequenceExercise(descendingFixture)
export const ascendingExercise: SequenceExercise = {
  exerciseId: 'sequence-10-12', schemaVersion: 1, type: 'SEQUENCE', visualModel: 'NUMBER_LINE',
  difficulty: 'EASY', operands: { terms: [10, 12] },
}
