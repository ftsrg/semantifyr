/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createReconnectController,
  type ReconnectControllerOptions,
  type ReconnectStatus,
} from '../lib/session/reconnectController'

const BACKOFF = [1000, 2000, 4000] as const

interface Harness {
  statuses: { status: ReconnectStatus; info?: string }[]
  attemptResults: ('success' | 'fail')[]
  cancelledRef: { value: boolean }
  controller: ReturnType<typeof createReconnectController>
  options: ReconnectControllerOptions
}

function harness(overrides?: Partial<ReconnectControllerOptions>): Harness {
  const statuses: Harness['statuses'] = []
  const attemptResults: ('success' | 'fail')[] = []
  const cancelledRef = { value: false }
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
    isCancelled: () => cancelledRef.value,
    onStatus: (status, info) => {
      statuses.push(info !== undefined ? { status, info } : { status })
    },
    ...overrides,
  }
  const controller = createReconnectController(options)
  return { statuses, attemptResults, cancelledRef, controller, options }
}

describe('reconnectController', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('walks the backoff sequence and gives up after maxAttempts', async () => {
    const h = harness()
    h.attemptResults.push('fail', 'fail', 'fail')

    h.controller.schedule()
    expect(h.statuses[0]?.status).toBe('reconnecting')
    expect(h.statuses[0]?.info).toMatch(/attempt 1\/3/)
    expect(h.controller.attempts()).toBe(1)

    await vi.advanceTimersByTimeAsync(BACKOFF[0])
    expect(h.statuses[1]?.info).toMatch(/attempt 2\/3/)

    await vi.advanceTimersByTimeAsync(BACKOFF[1])
    expect(h.statuses[2]?.info).toMatch(/attempt 3\/3/)

    await vi.advanceTimersByTimeAsync(BACKOFF[2])
    expect(h.statuses.at(-1)?.status).toBe('errored')
  })

  it('resets the attempt counter on a successful connect', async () => {
    const h = harness()
    h.attemptResults.push('fail', 'success')

    h.controller.schedule()
    await vi.advanceTimersByTimeAsync(BACKOFF[0])
    // After fail, the controller schedules attempt 2 immediately.
    expect(h.controller.attempts()).toBe(2)

    await vi.advanceTimersByTimeAsync(BACKOFF[1])
    expect(h.controller.attempts()).toBe(0)
  })

  it('stops attempting once isCancelled returns true', async () => {
    const h = harness()
    h.attemptResults.push('fail', 'fail')

    h.controller.schedule()
    h.cancelledRef.value = true
    await vi.advanceTimersByTimeAsync(BACKOFF[0])
    // The fail callback observed cancelled=true and did not schedule attempt 2.
    expect(h.statuses.filter((s) => s.status === 'reconnecting')).toHaveLength(1)
  })

  it('cancel clears a pending timer without firing the connect', async () => {
    const connect = vi.fn(async () => {})
    const h = harness({ connect })
    h.controller.schedule()
    h.controller.cancel()
    await vi.advanceTimersByTimeAsync(BACKOFF[2] + 100)
    expect(connect).not.toHaveBeenCalled()
  })

  it('resetAttempts forces the counter back to zero', () => {
    const h = harness()
    h.attemptResults.push('fail', 'fail', 'fail')
    h.controller.schedule()
    expect(h.controller.attempts()).toBe(1)
    h.controller.resetAttempts()
    expect(h.controller.attempts()).toBe(0)
  })

  it('reuses the last backoff entry for attempts past the array end', async () => {
    const connect = vi.fn(async () => {
      throw new Error('boom')
    })
    const h = harness({ maxAttempts: 5, backoffMs: [100], connect })
    h.controller.schedule()
    for (let i = 0; i < 5; i++) {
      await vi.advanceTimersByTimeAsync(100)
    }
    // Five failed attempts then errored.
    expect(connect).toHaveBeenCalledTimes(5)
    expect(h.statuses.at(-1)?.status).toBe('errored')
  })
})
