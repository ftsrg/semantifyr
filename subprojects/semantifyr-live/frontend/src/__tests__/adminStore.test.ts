/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { createAdminStore } from '../admin/adminStore'
import { ApiError, type AdminConfigResponse, type AdminStatusResponse, type InfoResponse, type LiveServerApi } from '../lib/api'
import { fakeSession } from './fixtures/sessions'

// Goal: the page's state follows the server: the polling and its pause, the refusal, the acting calls and their notices

const STATUS: AdminStatusResponse = {
  sessions: [fakeSession({ sessionId: 'session-one' })],
  load: { activeSessions: 1, maxSessions: 64, runningVerifications: 0, queuedVerifications: 0, verificationPermits: 4, uptime: 'PT1H' },
}

const CONFIG: AdminConfigResponse = {
  server: { port: 8080, pingPeriod: 'PT10S', pingTimeout: 'PT30S', webRootDirectory: null, wsHandshakesPerPeriod: 10, wsHandshakeRatePeriod: 'PT1M', maxWsFrameSize: 1024 },
  sessionManager: { maxSessionsGlobal: 64, semanticLibrariesDirectory: null, rootWorkDirectory: '/work' },
  verification: { concurrency: 4, timeout: 'PT1M' },
  portfolios: [],
}

const INFO: InfoResponse = {
  uptime: 'PT1H',
  startedAt: '2026-01-01T00:00:00Z',
  version: '1.0',
  commit: 'abc',
  buildTime: 'now',
  activeSessions: 1,
  maxSessions: 64,
}

function fakeApi(overrides: Partial<LiveServerApi> = {}): LiveServerApi {
  return {
    httpBase: 'http://live.example',
    fetchPortfolios: vi.fn(() => {
      return Promise.resolve({ portfolios: [], defaultPortfolio: 'smart-full' })
    }),
    fetchFlavors: vi.fn(() => {
      return Promise.resolve([])
    }),
    fetchFlavor: vi.fn(() => {
      return Promise.resolve(null)
    }),
    fetchInfo: vi.fn(() => {
      return Promise.resolve(INFO)
    }),
    fetchAdminUser: vi.fn(() => {
      return Promise.resolve('octocat')
    }),
    fetchAdminConfig: vi.fn(() => {
      return Promise.resolve(CONFIG)
    }),
    fetchAdminStatus: vi.fn(() => {
      return Promise.resolve(STATUS)
    }),
    cancelSession: vi.fn(() => {
      return Promise.resolve(true)
    }),
    cancelVerification: vi.fn(() => {
      return Promise.resolve(true)
    }),
    ...overrides,
  }
}

describe('the admin store', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('loads the status, the user and the catalogue when started', async () => {
    const store = createAdminStore(fakeApi())
    const stop = store.start()
    await vi.advanceTimersByTimeAsync(0)
    expect(store.state.get().data?.status.sessions).toHaveLength(1)
    expect(store.state.get().user).toBe('octocat')
    expect(store.state.get().error).toBeNull()
    stop()
  })

  it('refreshes every interval until stopped', async () => {
    const api = fakeApi()
    const store = createAdminStore(api, 1000)
    const stop = store.start()
    await vi.advanceTimersByTimeAsync(3000)
    expect(api.fetchAdminStatus).toHaveBeenCalledTimes(4)
    stop()
    await vi.advanceTimersByTimeAsync(3000)
    expect(api.fetchAdminStatus).toHaveBeenCalledTimes(4)
  })

  it('stops refreshing while paused and refreshes at once when resumed', async () => {
    const api = fakeApi()
    const store = createAdminStore(api, 1000)
    const stop = store.start()
    await vi.advanceTimersByTimeAsync(0)
    store.togglePause()
    await vi.advanceTimersByTimeAsync(5000)
    expect(store.state.get().paused).toBe(true)
    expect(api.fetchAdminStatus).toHaveBeenCalledTimes(1)
    store.togglePause()
    await vi.advanceTimersByTimeAsync(0)
    expect(api.fetchAdminStatus).toHaveBeenCalledTimes(2)
    await vi.advanceTimersByTimeAsync(1000)
    expect(api.fetchAdminStatus).toHaveBeenCalledTimes(3)
    stop()
  })

  it('records a refusal and keeps the last data when a later refresh fails', async () => {
    const fetchAdminStatus = vi.fn<LiveServerApi['fetchAdminStatus']>()
    fetchAdminStatus.mockRejectedValueOnce(new ApiError(403, 'Not signed in through the proxy'))
    const store = createAdminStore(fakeApi({ fetchAdminStatus }))
    const stop = store.start()
    await vi.advanceTimersByTimeAsync(0)
    expect(store.state.get().data).toBeNull()
    expect(store.state.get().error).toEqual({ status: 403, message: 'Not signed in through the proxy' })

    fetchAdminStatus.mockResolvedValueOnce(STATUS)
    await store.refresh()
    expect(store.state.get().error).toBeNull()

    fetchAdminStatus.mockRejectedValueOnce(new Error('network down'))
    await store.refresh()
    expect(store.state.get().error).toEqual({ status: null, message: 'network down' })
    expect(store.state.get().data).not.toBeNull()
    stop()
  })

  it('kills a session after the second press and tells the result', async () => {
    const api = fakeApi()
    const store = createAdminStore(api)
    store.askKill('session-one')
    expect(store.state.get().confirmingKill).toBe('session-one')
    await store.kill('session-one')
    expect(api.cancelSession).toHaveBeenCalledWith('session-one')
    expect(api.fetchAdminStatus).toHaveBeenCalledTimes(1)
    expect(store.state.get().confirmingKill).toBeNull()
    expect(store.state.get().notice).toBe('Killed session session-')
  })

  it('says so when a cancel does not go through, and clears the notice later', async () => {
    const api = fakeApi({
      cancelVerification: vi.fn(() => {
        return Promise.resolve(false)
      }),
    })
    const store = createAdminStore(api)
    await store.cancelVerification('verification-1')
    expect(store.state.get().notice).toBe('Could not cancel verification verification-1')
    await vi.advanceTimersByTimeAsync(5000)
    expect(store.state.get().notice).toBeNull()
  })

  it('opens and closes a session, and keeps the filter', () => {
    const store = createAdminStore(fakeApi())
    store.toggleExpanded('session-one')
    expect(store.state.get().expanded.has('session-one')).toBe(true)
    store.toggleExpanded('session-one')
    expect(store.state.get().expanded.has('session-one')).toBe(false)
    store.setFilter('embed')
    expect(store.state.get().filter).toBe('embed')
  })
})
