/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  CloseCode,
  createReconnectController,
  observeSocketClose,
  type ReconnectControllerOptions,
  type ReconnectStatus,
  type SocketClose,
} from '../lib/session/reconnectController'

const BACKOFF = [1000, 2000, 4000] as const

interface Harness {
  statuses: { status: ReconnectStatus; info?: string }[]
  attemptResults: ('success' | 'fail')[]
  cancelled: { value: boolean }
  close: { value: SocketClose | null }
  controller: ReturnType<typeof createReconnectController>
  options: ReconnectControllerOptions
}

function createHarness(overrides?: Partial<ReconnectControllerOptions>): Harness {
  const statuses: Harness['statuses'] = []
  const attemptResults: ('success' | 'fail')[] = []
  const cancelled = { value: false }
  const close: Harness['close'] = { value: null }
  const options: ReconnectControllerOptions = {
    maxAttempts: 3,
    backoffMs: BACKOFF,
    connect: async () => {
      const next = attemptResults.shift()
      if (next === 'success') {
        return
      }
      throw new Error('connect failed')
    },
    isCancelled: () => cancelled.value,
    lastClose: () => close.value,
    onStatus: (status, info) => {
      statuses.push(info !== undefined ? { status, info } : { status })
    },
    ...overrides,
  }
  const controller = createReconnectController(options)
  return { statuses, attemptResults, cancelled, close, controller, options }
}

describe('reconnectController', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('walks the backoff sequence and gives up after maxAttempts', async () => {
    const harness = createHarness()
    harness.attemptResults.push('fail', 'fail', 'fail')

    harness.controller.schedule()
    expect(harness.statuses[0]?.status).toBe('reconnecting')
    expect(harness.statuses[0]?.info).toMatch(/attempt 1\/3/)
    expect(harness.controller.attempts()).toBe(1)

    await vi.advanceTimersByTimeAsync(BACKOFF[0])
    expect(harness.statuses[1]?.info).toMatch(/attempt 2\/3/)

    await vi.advanceTimersByTimeAsync(BACKOFF[1])
    expect(harness.statuses[2]?.info).toMatch(/attempt 3\/3/)

    await vi.advanceTimersByTimeAsync(BACKOFF[2])
    expect(harness.statuses.at(-1)?.status).toBe('errored')
  })

  it('resets the attempt counter on a successful connect', async () => {
    const harness = createHarness()
    harness.attemptResults.push('fail', 'success')

    harness.controller.schedule()
    await vi.advanceTimersByTimeAsync(BACKOFF[0])
    // After fail, the controller schedules attempt 2 immediately.
    expect(harness.controller.attempts()).toBe(2)

    await vi.advanceTimersByTimeAsync(BACKOFF[1])
    expect(harness.controller.attempts()).toBe(0)
  })

  it('emits connected status when the connect attempt succeeds', async () => {
    const harness = createHarness()
    harness.attemptResults.push('success')

    harness.controller.schedule()
    await vi.advanceTimersByTimeAsync(BACKOFF[0])
    expect(harness.statuses.at(-1)?.status).toBe('connected')
  })

  it('stops attempting once isCancelled returns true', async () => {
    const harness = createHarness()
    harness.attemptResults.push('fail', 'fail')

    harness.controller.schedule()
    harness.cancelled.value = true
    await vi.advanceTimersByTimeAsync(BACKOFF[0])
    // The fail callback observed cancelled=true and did not schedule attempt 2.
    expect(harness.statuses.filter((entry) => entry.status === 'reconnecting')).toHaveLength(1)
  })

  it('cancel clears a pending timer without firing the connect', async () => {
    const connect = vi.fn(async () => {})
    const harness = createHarness({ connect })
    harness.controller.schedule()
    harness.controller.cancel()
    await vi.advanceTimersByTimeAsync(BACKOFF[2] + 100)
    expect(connect).not.toHaveBeenCalled()
  })

  it('resetAttempts forces the counter back to zero', () => {
    const harness = createHarness()
    harness.attemptResults.push('fail', 'fail', 'fail')
    harness.controller.schedule()
    expect(harness.controller.attempts()).toBe(1)
    harness.controller.resetAttempts()
    expect(harness.controller.attempts()).toBe(0)
  })

  it('reuses the last backoff entry for attempts past the array end', async () => {
    const connect = vi.fn(async () => {
      throw new Error('boom')
    })
    const harness = createHarness({ maxAttempts: 5, backoffMs: [100], connect })
    harness.controller.schedule()
    for (let attempt = 0; attempt < 5; attempt++) {
      await vi.advanceTimersByTimeAsync(100)
    }
    // Five failed attempts then errored.
    expect(connect).toHaveBeenCalledTimes(5)
    expect(harness.statuses.at(-1)?.status).toBe('errored')
  })

  it.each([
    [CloseCode.SessionTerminated, 'Session terminated'],
    [CloseCode.MissingFlavor, 'Missing flavor parameter'],
    [CloseCode.UnknownFlavor, 'Unknown flavor: nope'],
  ])('stops for good on close code %i and shows the server reason', async (code, reason) => {
    const connect = vi.fn(async () => {})
    const harness = createHarness({ connect })
    harness.close.value = { code, reason }

    harness.controller.schedule()
    harness.controller.schedule()
    await vi.advanceTimersByTimeAsync(BACKOFF[2] + 100)

    expect(harness.statuses).toEqual([{ status: 'terminated', info: reason }])
    expect(harness.controller.terminated()).toBe(true)
    expect(connect).not.toHaveBeenCalled()
  })

  it('keeps stopping after a terminal close even when a later close would retry', async () => {
    const connect = vi.fn(async () => {})
    const harness = createHarness({ connect })
    harness.close.value = { code: CloseCode.SessionTerminated, reason: 'Session terminated' }
    harness.controller.schedule()

    harness.close.value = { code: 1006, reason: '' }
    harness.controller.schedule()
    await vi.advanceTimersByTimeAsync(BACKOFF[2] + 100)

    expect(connect).not.toHaveBeenCalled()
  })

  it('retries a session limit rejection with the backoff and shows the reason', async () => {
    const harness = createHarness()
    harness.close.value = { code: CloseCode.TooManySessions, reason: 'Session limit reached' }
    harness.attemptResults.push('fail', 'fail', 'fail')

    harness.controller.schedule()
    expect(harness.statuses[0]).toEqual({ status: 'reconnecting', info: 'Session limit reached, reconnecting (attempt 1/3)...' })

    await vi.advanceTimersByTimeAsync(BACKOFF[0] + BACKOFF[1] + BACKOFF[2])
    expect(harness.statuses.at(-1)).toEqual({ status: 'errored', info: 'Session limit reached' })
    expect(harness.controller.terminated()).toBe(false)
  })

  it.each([
    1000,
    1006,
    4500,
  ])('retries a close with code %i as a lost connection', async (code) => {
    const harness = createHarness()
    harness.close.value = { code, reason: 'Session ended' }
    harness.attemptResults.push('success')

    harness.controller.schedule()
    expect(harness.statuses[0]).toEqual({ status: 'reconnecting', info: 'Reconnecting (attempt 1/3)...' })

    await vi.advanceTimersByTimeAsync(BACKOFF[0])
    expect(harness.statuses.at(-1)?.status).toBe('connected')
  })
})

describe('observeSocketClose', () => {
  it('reports the close code and reason after the onclose handler is overwritten and before it runs', () => {
    const socket = new WebSocket('ws://127.0.0.1:9/ws/lsp/oxsts')
    const order: string[] = []
    const closes: SocketClose[] = []
    socket.onclose = () => {
      order.push('reader')
    }
    observeSocketClose(socket, (close) => {
      order.push('observer')
      closes.push(close)
    })
    socket.onclose = () => {
      order.push('client')
    }

    socket.dispatchEvent(new CloseEvent('close', { code: CloseCode.SessionTerminated, reason: 'Session terminated' }))

    expect(closes).toEqual([{ code: CloseCode.SessionTerminated, reason: 'Session terminated' }])
    expect(order).toEqual(['observer', 'client'])
    socket.onclose = null
    socket.close()
  })
})
