// SPDX-License-Identifier: AGPL-3.0-or-later
import type {
  ActionMathState, AssessmentResultPayload, NextAction, StrategyEvidenceState,
} from '../../src/contracts/assessment'

const result = {
  schemaVersion: 1,
  actionMathState: 'CORRECTA',
  strategyEvidenceState: 'COHERENTE',
  nextAction: 'CONTINUAR',
  ruleIds: [],
  assessmentVersion: 'synthetic-v1',
} as const satisfies AssessmentResultPayload

const math: readonly ActionMathState[] = ['CORRECTA', 'INCORRECTA', 'SIN_TERMINAR']
const evidence: readonly StrategyEvidenceState[] = [
  'COHERENTE', 'ERROR_ARITMETICO_IDENTIFICABLE', 'INSUFICIENTE', 'CONFLICTO_ASR', 'NO_EVALUABLE',
]
const next: readonly NextAction[] = ['CONTINUAR', 'PISTA', 'PEDIR_ACLARACION', 'REPETIR_AUDIO']

// Independent axes also permit correct actions with uncertain explanation evidence.
const conflict = { ...result, strategyEvidenceState: 'CONFLICTO_ASR', nextAction: 'PEDIR_ACLARACION' } as const satisfies AssessmentResultPayload
const insufficient = { ...result, strategyEvidenceState: 'INSUFICIENTE' } as const satisfies AssessmentResultPayload
const unavailable = { ...result, strategyEvidenceState: 'NO_EVALUABLE', nextAction: 'REPETIR_AUDIO' } as const satisfies AssessmentResultPayload

// @ts-expect-error Canonical states retain their exact Spanish identifiers.
const unknownMath: ActionMathState = 'CORRECT'
// @ts-expect-error Evidence is a distinct axis.
const unknownEvidence: StrategyEvidenceState = 'INCORRECTA'
// @ts-expect-error Next steps are bounded.
const unknownNext: NextAction = 'PENALIZAR'
// @ts-expect-error Only schema version 1 is supported.
const future: AssessmentResultPayload = { ...result, schemaVersion: 2 }
// @ts-expect-error Enums are strings, never ordinals.
const ordinal: AssessmentResultPayload = { ...result, actionMathState: 0 }
// @ts-expect-error All fields, including provenance, are required.
const missing: AssessmentResultPayload = { schemaVersion: 1, actionMathState: 'CORRECTA' }
// @ts-expect-error Rule IDs must be strings.
const numericRule: AssessmentResultPayload = { ...result, ruleIds: [1] }
// @ts-expect-error The decision version is a string, independent of schemaVersion.
const numericVersion: AssessmentResultPayload = { ...result, assessmentVersion: 1 }
// @ts-expect-error Null is not a state.
const nullState: AssessmentResultPayload = { ...result, strategyEvidenceState: null }

function readonlyContract(payload: AssessmentResultPayload): void {
  // @ts-expect-error States cannot be changed in place.
  payload.actionMathState = 'INCORRECTA'
  // @ts-expect-error The provenance list is readonly.
  payload.ruleIds.push('another-rule')
  // @ts-expect-error Provenance cannot be replaced in place.
  payload.ruleIds = []
  // @ts-expect-error Version is readonly.
  payload.assessmentVersion = 'other'
}

void [result, math, evidence, next, conflict, insufficient, unavailable, readonlyContract]
