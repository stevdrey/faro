// SPDX-License-Identifier: AGPL-3.0-or-later
import fixture from '../../../../../contracts/exercises/v1/fixtures/subtraction.json' with { type: 'json' }
import { toSubtractionExercise } from './fixture.ts'

export const subtractionExercise = toSubtractionExercise(fixture)
