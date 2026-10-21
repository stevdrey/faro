// SPDX-License-Identifier: AGPL-3.0-or-later
import type { AssessmentResultPayload } from '../../src/contracts/assessment'
import coherent from '../../../../contracts/assessments/v1/fixtures/coherent.json'
import arithmeticError from '../../../../contracts/assessments/v1/fixtures/arithmetic-error.json'
import insufficient from '../../../../contracts/assessments/v1/fixtures/insufficient.json'
import conflict from '../../../../contracts/assessments/v1/fixtures/asr-conflict.json'
import notEvaluable from '../../../../contracts/assessments/v1/fixtures/not-evaluable.json'
import incomplete from '../../../../contracts/assessments/v1/fixtures/incomplete.json'

// JSON imports widen literals. Java fixture tests verify exact versions and enum values.
type Shape = {
  schemaVersion: number
  actionMathState: string
  strategyEvidenceState: string
  nextAction: string
  ruleIds: readonly string[]
  assessmentVersion: string
}
type Exact<A, B> = [A] extends [B] ? ([B] extends [A] ? true : false) : false
const keys: [
  Exact<keyof typeof coherent, keyof AssessmentResultPayload>,
  Exact<keyof typeof arithmeticError, keyof AssessmentResultPayload>,
  Exact<keyof typeof insufficient, keyof AssessmentResultPayload>,
  Exact<keyof typeof conflict, keyof AssessmentResultPayload>,
  Exact<keyof typeof notEvaluable, keyof AssessmentResultPayload>,
  Exact<keyof typeof incomplete, keyof AssessmentResultPayload>,
] = [true, true, true, true, true, true]
const fixtures: readonly Shape[] = [coherent, arithmeticError, insufficient, conflict, notEvaluable, incomplete]
void [keys, fixtures]
