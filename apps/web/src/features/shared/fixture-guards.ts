// SPDX-License-Identifier: AGPL-3.0-or-later
import type { Difficulty, VisualModel } from '../../contracts/exercise.ts'

export const VISUAL_MODELS: readonly VisualModel[] = ['BLOCKS', 'NUMBER_LINE', 'TENS_AND_ONES']
export const DIFFICULTIES: readonly Difficulty[] = ['EASY', 'MEDIUM', 'HARD']

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

export function isOneOf<T extends string>(options: readonly T[], value: unknown): value is T {
  return (options as readonly unknown[]).includes(value)
}
