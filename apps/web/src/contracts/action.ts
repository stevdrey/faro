// SPDX-License-Identifier: AGPL-3.0-or-later
export interface EventMetadata {
  readonly step: number
  /** ISO 8601 UTC timestamp ending in Z. Steps, not timestamps, define order. */
  readonly occurredAt: string
}

export type PlaceBlock = EventMetadata & { readonly kind: 'placeBlock'; readonly value: number }
export type RemoveBlock = EventMetadata & { readonly kind: 'removeBlock'; readonly value: number }
export type JumpLeft = EventMetadata & { readonly kind: 'jumpLeft'; readonly value: number }
export type JumpRight = EventMetadata & { readonly kind: 'jumpRight'; readonly value: number }
export type FinishAttempt = EventMetadata & {
  readonly kind: 'finishAttempt'
  /** Forbid magnitudes on structural assignments, including spreads and intermediate variables. */
  readonly value?: never
}

export type ActionEvent = PlaceBlock | RemoveBlock | JumpLeft | JumpRight | FinishAttempt
export type ActionKind = ActionEvent['kind']

/** Runtime validation of untrusted JSON belongs at the API boundary. */
export interface ActionTracePayload {
  readonly schemaVersion: 1
  readonly initialValue: number
  readonly events: readonly ActionEvent[]
}
