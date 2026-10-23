// SPDX-License-Identifier: AGPL-3.0-or-later
import type { ActionEvent, EventMetadata } from './action.ts'

export type Exchange = EventMetadata & {
  readonly kind: 'decomposeTen' | 'composeTen'
  readonly value?: never
}

export type ActionEventV2 = ActionEvent | Exchange

export interface ActionTracePayloadV2 {
  readonly schemaVersion: 2
  /** Nonnegative signed 32-bit starting quantity. */
  readonly initialValue: number
  readonly events: readonly ActionEventV2[]
}
