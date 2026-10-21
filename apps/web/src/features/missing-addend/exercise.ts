// SPDX-License-Identifier: AGPL-3.0-or-later
import fixture from '../../../../../contracts/exercises/v1/fixtures/missing-addend-known-first.json'
import type { MissingAddendExercise } from '../../contracts/exercise.ts'

// Trusted repository fixture, not an API response. No solution is sent to this screen.
export const demoExercise: MissingAddendExercise = {
  ...fixture,
  schemaVersion: 1,
  type: 'MISSING_ADDEND',
  visualModel: 'BLOCKS',
  difficulty: 'EASY',
  presentation: 'KNOWN_PLUS_MISSING_EQUALS_TOTAL',
}
