// SPDX-License-Identifier: AGPL-3.0-or-later
export type ActionMathState = 'CORRECTA' | 'INCORRECTA' | 'SIN_TERMINAR'
export type StrategyEvidenceState =
  | 'COHERENTE'
  | 'ERROR_ARITMETICO_IDENTIFICABLE'
  | 'INSUFICIENTE'
  | 'CONFLICTO_ASR'
  | 'NO_EVALUABLE'
export type NextAction = 'CONTINUAR' | 'PISTA' | 'PEDIR_ACLARACION' | 'REPETIR_AUDIO'

/** Independent axes. These types do not validate untrusted JSON or choose a feedback policy. */
export interface AssessmentResultPayload {
  readonly schemaVersion: 1
  readonly actionMathState: ActionMathState
  readonly strategyEvidenceState: StrategyEvidenceState
  readonly nextAction: NextAction
  readonly ruleIds: readonly string[]
  /** Nonblank decision version, independent of the wire schema version. */
  readonly assessmentVersion: string
}
