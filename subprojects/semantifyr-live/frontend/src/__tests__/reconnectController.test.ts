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
  cancelled: { value: boolean }
  controller: ReturnType<typeof createReconnectController>
  options: ReconnectControllerOptions
}

function createHarness(overrides?: Partial<ReconnectControllerOptions>): Harness {
  const statuses: Harness['statuses'] = []
  const attemptResults: ('success' | 'fail')[] = []
  const cancelled = { value: false }
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
    onStatus: (status, info) => {
      statuses.push(info !== undefined ? { status, info } : { status })
    },
    ...overrides,
  }
  const controller = createReconnectController(options)
  return { statuses, attemptResults, cancelled, controller, options }
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
})
