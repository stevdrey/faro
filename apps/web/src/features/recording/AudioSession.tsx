// SPDX-License-Identifier: AGPL-3.0-or-later
import { createContext, useContext, useState, useSyncExternalStore } from 'react'
import type { ReactNode } from 'react'
import { AudioCoordinator } from './audio-coordinator.ts'
const AudioSession = createContext<AudioCoordinator | null>(null)
const noSubscribe = () => () => {}
const noCapture = () => false
export function AudioSessionProvider({ children }: { children: ReactNode }) {
  const [coordinator] = useState(() => new AudioCoordinator())
  return <AudioSession.Provider value={coordinator}>{children}</AudioSession.Provider>
}
export function useAudioSession() {
  const coordinator = useContext(AudioSession)
  const capturing = useSyncExternalStore(coordinator?.subscribe ?? noSubscribe, coordinator?.isCapturing ?? noCapture)
  return { coordinator, capturing }
}
