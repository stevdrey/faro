// SPDX-License-Identifier: AGPL-3.0-or-later
import fixture from '../../../../../contracts/exercises/v1/fixtures/missing-addend-known-first.json'
import { toMissingAddendExercise } from './fixture.ts'

// Trusted repository fixture, not an API response. No solution is sent to this screen.
export const demoExercise = toMissingAddendExercise(fixture)
