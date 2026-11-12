/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import {
  ApiError,
  type AdminConfigResponse,
  type AdminStatusResponse,
  type FlavorInfo,
  type InfoResponse,
  type LiveServerApi,
  type PortfolioInfo,
} from '../lib/api'
import { createStore, type Store } from '../lib/stores/store'

export const REFRESH_INTERVAL_MS = 1000
const NOTICE_MS = 4000

export interface AdminData {
  status: AdminStatusResponse
  config: AdminConfigResponse
  info: InfoResponse
}

export interface AdminError {
  status: number | null
  message: string
}

export interface AdminState {
  data: AdminData | null
  user: string | null
  flavors: readonly FlavorInfo[]
  portfolios: readonly PortfolioInfo[]
  error: AdminError | null
  refreshedAt: number | null
  paused: boolean
  filter: string
  expanded: ReadonlySet<string>
  // the session whose kill waits for a second press
  confirmingKill: string | null
  notice: string | null
}

export interface AdminStore {
  state: Store<AdminState>
  start: () => () => void
  refresh: () => Promise<void>
  togglePause: () => void
  setFilter: (filter: string) => void
  toggleExpanded: (sessionId: string) => void
  askKill: (sessionId: string | null) => void
  kill: (sessionId: string) => Promise<void>
  cancelVerification: (verificationId: string) => Promise<void>
}

function describe(failure: unknown): AdminError {
  if (failure instanceof ApiError) {
    return { status: failure.status, message: failure.message }
  }
  return { status: null, message: failure instanceof Error ? failure.message : String(failure) }
}

export function createAdminStore(api: LiveServerApi, intervalMs: number = REFRESH_INTERVAL_MS): AdminStore {
  const state = createStore<AdminState>({
    data: null,
    user: null,
    flavors: [],
    portfolios: [],
    error: null,
    refreshedAt: null,
    paused: false,
    filter: '',
    expanded: new Set(),
    confirmingKill: null,
    notice: null,
  })
  let timer: ReturnType<typeof setInterval> | null = null
  let noticeTimer: ReturnType<typeof setTimeout> | null = null

  const change = (partial: Partial<AdminState>): void => {
    state.update((previous) => {
      return { ...previous, ...partial }
    })
  }

  const announce = (notice: string): void => {
    change({ notice })
    if (noticeTimer !== null) {
      clearTimeout(noticeTimer)
    }
    noticeTimer = setTimeout(() => {
      change({ notice: null })
    }, NOTICE_MS)
  }

  const refresh = async (): Promise<void> => {
    try {
      const [status, config, info] = await Promise.all([
        api.fetchAdminStatus(),
        api.fetchAdminConfig(),
        api.fetchInfo(),
      ])
      change({ data: { status, config, info }, error: null, refreshedAt: Date.now() })
    } catch (failure) {
      change({ error: describe(failure) })
    }
  }

  const schedule = (): void => {
    timer ??= setInterval(() => {
      void refresh()
    }, intervalMs)
  }

  const unschedule = (): void => {
    if (timer !== null) {
      clearInterval(timer)
      timer = null
    }
  }

  const act = async (request: Promise<boolean>, success: string, failure: string): Promise<void> => {
    const done = await request.catch(() => {
      return false
    })
    announce(done ? success : failure)
    await refresh()
  }

  return {
    state,
    start: () => {
      void refresh()
      void api.fetchAdminUser().then((user) => {
        change({ user })
      }, () => {})
      void api.fetchFlavors().then((flavors) => {
        change({ flavors })
      }, () => {})
      void api.fetchPortfolios().then((response) => {
        change({ portfolios: response.portfolios })
      }, () => {})
      schedule()
      return () => {
        unschedule()
        if (noticeTimer !== null) {
          clearTimeout(noticeTimer)
        }
      }
    },
    refresh,
    togglePause: () => {
      const paused = !state.get().paused
      change({ paused })
      if (paused) {
        unschedule()
      } else {
        schedule()
        void refresh()
      }
    },
    setFilter: (filter) => {
      change({ filter })
    },
    toggleExpanded: (sessionId) => {
      state.update((previous) => {
        const expanded = new Set(previous.expanded)
        if (!expanded.delete(sessionId)) {
          expanded.add(sessionId)
        }
        return { ...previous, expanded }
      })
    },
    askKill: (sessionId) => {
      change({ confirmingKill: sessionId })
    },
    kill: async (sessionId) => {
      change({ confirmingKill: null })
      await act(api.cancelSession(sessionId), `Killed session ${sessionId.slice(0, 8)}`, `Could not kill session ${sessionId.slice(0, 8)}`)
    },
    cancelVerification: async (verificationId) => {
      await act(api.cancelVerification(verificationId), `Cancelled verification ${verificationId}`, `Could not cancel verification ${verificationId}`)
    },
  }
}
