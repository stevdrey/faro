// SPDX-License-Identifier: AGPL-3.0-or-later
import type { ActionEvent } from './action.ts'

export type Exchange = {
  readonly kind: 'decomposeTen' | 'composeTen'
  readonly step: number
  /** ISO 8601 UTC; steps define order. */
  readonly occurredAt: string
  readonly value?: never
}

export type ActionEventV2 = ActionEvent | Exchange

export interface ActionTracePayloadV2 {
  readonly schemaVersion: 2
  readonly initialValue: number
  readonly events: readonly ActionEventV2[]
}
