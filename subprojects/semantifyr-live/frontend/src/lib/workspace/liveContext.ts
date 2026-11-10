/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { createContext, useContext } from 'react'
import type { LanguageRuntime } from '../language/types'
import type { ReadableStore } from '../stores/store'
import { useStore } from '../stores/useStore'
import type { VerificationState } from '../verification/engine'
import type { Live } from './live'
import { NO_VERIFICATION } from './verification'

export const LiveContext = createContext<Live | null>(null)

export function useLive(): Live {
  const live = useContext(LiveContext)
  if (!live) {
    throw new Error('useLive needs a LiveContext provider')
  }
  return live
}

export function useCurrentVerification(live: Live): VerificationState {
  const key = useStore(live.workspace, (workspace) => {
    return workspace.documentKey
  })
  return useStore(live.verification, (states) => {
    return key === null ? NO_VERIFICATION : states.get(key) ?? NO_VERIFICATION
  })
}

export function useRuntime(live: Live): LanguageRuntime | null {
  return useStore(live.workspace, (workspace) => {
    return workspace.runtime
  })
}

// reads a store of the current runtime, the fallback while no runtime runs
export function useRuntimeStore<T>(
  live: Live,
  storeOf: (runtime: LanguageRuntime) => ReadableStore<T>,
  fallback: ReadableStore<T>,
): T {
  const runtime = useRuntime(live)
  return useStore(runtime ? storeOf(runtime) : fallback)
}
