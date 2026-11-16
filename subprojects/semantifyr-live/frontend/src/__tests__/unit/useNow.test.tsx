/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { useNow } from '../../shells/hooks'

// Goal: a render between two ticks, as a changed state causes, reads the time of that render, so a countdown never shows a second more than is left
describe('useNow', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('reads the time of a render between two ticks', () => {
    vi.useFakeTimers()
    const { result, rerender } = renderHook(() => {
      return useNow(1000)
    })

    vi.advanceTimersByTime(1600)
    rerender()

    expect(result.current).toBe(Date.now())
  })
})
