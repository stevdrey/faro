// SPDX-License-Identifier: AGPL-3.0-or-later
/** One playback owner per application; capture excludes all playback. Leases ignore stale releases. */
export class AudioCoordinator {
  private playback: { stop: () => void } | null = null
  private capture: object | null = null
  private listeners = new Set<() => void>()
  isCapturing = () => this.capture !== null
  subscribe = (listener: () => void) => {
    this.listeners.add(listener)
    return () => { this.listeners.delete(listener) }
  }
  private notify() { for (const listener of this.listeners) listener() }
  stopPlayback() {
    const owner = this.playback
    this.playback = null
    owner?.stop()
  }
  play(stop: () => void): (() => void) | null {
    if (this.capture) return null
    this.stopPlayback()
    const owner = { stop }
    this.playback = owner
    return () => { if (this.playback === owner) this.playback = null }
  }
  record(): (() => void) | null {
    if (this.capture) return null
    const owner = {}
    this.capture = owner
    this.stopPlayback()
    this.notify()
    return () => {
      if (this.capture !== owner) return
      this.capture = null
      this.notify()
    }
  }
}
