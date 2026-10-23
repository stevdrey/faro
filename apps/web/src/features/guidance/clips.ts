// SPDX-License-Identifier: AGPL-3.0-or-later
import type { Exercise } from '../../contracts/exercise.ts'
import { demoExercise } from '../missing-addend/exercise.ts'
import { ascendingExercise, descendingExercise } from '../sequences/exercise.ts'
import { subtractionExercise } from '../subtraction/exercise.ts'

/** Fixed UI speech only: never derive prompts from a child's actions or transcript. */
export const clips = {
  blocks: 'Ya tenemos doce bloques. Agregá los que faltan para llegar a dieciocho. Podés usar más o menos, o mover los bloques. Cuando terminés, tocá Listo.',
  descending: 'Treinta, veintiocho. ¿Qué número sigue? Elegí el tamaño del salto y usá las flechas. Podés cambiar de idea. Cuando terminés, tocá Listo.',
  ascending: 'Diez, doce. ¿Qué número sigue? Elegí el tamaño del salto y usá las flechas. Podés cambiar de idea. Cuando terminés, tocá Listo.',
  subtraction: 'Hay cincuenta y tres. Quitá veintiocho moviendo decenas y unidades. Podés cambiar una decena por diez unidades, y devolver piezas. Cuando terminés, tocá Listo.',
  next: 'Terminaste. Si querés volver a jugar, tocá el botón con la flecha que da la vuelta. También podés elegir otra actividad arriba.',
  recordingStart: 'Cuando estés listo, tocá el micrófono y contame cómo lo hiciste. Vos elegís cuándo empezar.',
  recordingStop: 'Cuando terminés de contarme, tocá el botón para detener la grabación. También podés cancelar.',
} as const

export type ClipId = keyof typeof clips
export function clipSource(id: ClipId, base = '/'): string {
  return `${base}audio/${id}.ogg`
}

const sameTerms = (left: readonly number[], right: readonly number[]) =>
  left.length === right.length && left.every((term, index) => term === right[index])

/** The fixed recordings name the fixtures' operands; other exercises must not play misleading numbers. */
export function exerciseClip(exercise: Exercise): ClipId | undefined {
  switch (exercise.type) {
    case 'MISSING_ADDEND': {
      const { knownAddend, total } = demoExercise.operands
      return exercise.operands.total === total && exercise.operands.knownAddend === knownAddend ? 'blocks' : undefined
    }
    case 'SUBTRACTION': {
      const { minuend, subtrahend } = subtractionExercise.operands
      return exercise.operands.minuend === minuend && exercise.operands.subtrahend === subtrahend ? 'subtraction' : undefined
    }
    case 'SEQUENCE': {
      if (sameTerms(exercise.operands.terms, descendingExercise.operands.terms)) return 'descending'
      return sameTerms(exercise.operands.terms, ascendingExercise.operands.terms) ? 'ascending' : undefined
    }
  }
}
