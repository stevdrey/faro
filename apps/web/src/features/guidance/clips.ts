// SPDX-License-Identifier: AGPL-3.0-or-later
import type { Exercise } from '../../contracts/exercise.ts'

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
  return `${base}audio/${id}.wav`
}

/** The fixed recordings name operands; unsupported exercises must not play misleading numbers. */
export function exerciseClip(exercise: Exercise): ClipId | undefined {
  switch (exercise.type) {
    case 'MISSING_ADDEND': return exercise.operands.total === 18 && exercise.operands.knownAddend === 12 ? 'blocks' : undefined
    case 'SUBTRACTION': return exercise.operands.minuend === 53 && exercise.operands.subtrahend === 28 ? 'subtraction' : undefined
    case 'SEQUENCE': {
      const { terms } = exercise.operands
      if (terms.length !== 2) return undefined
      if (terms[0] === 30 && terms[1] === 28) return 'descending'
      return terms[0] === 10 && terms[1] === 12 ? 'ascending' : undefined
    }
  }
}
