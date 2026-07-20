/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from 'vitest'
import { witnessIconDescriptor } from '../lib/verification/witnessIcon'

describe('witnessIconDescriptor', () => {
  it('returns simple state when no validation has run', () => {
    const d = witnessIconDescriptor(undefined)
    expect(d.kind).toBe('simple')
    expect(d.badgeVisible).toBe(false)
    expect(d.ariaLabel).toBe('Show witness')
  })

  it('returns the quiet valid state on success', () => {
    const d = witnessIconDescriptor('valid')
    expect(d.kind).toBe('valid')
    expect(d.badgeVisible).toBe(true)
    expect(d.badgeColor).toBe('success')
    expect(d.ariaLabel).toMatch(/passed/)
  })

  it('folds errored into the inconclusive bucket', () => {
    const d = witnessIconDescriptor('errored')
    expect(d.kind).toBe('inconclusive')
    expect(d.badgeColor).toBe('warning')
  })

  it('returns the danger failed state on invalid', () => {
    const d = witnessIconDescriptor('invalid')
    expect(d.kind).toBe('failed')
    expect(d.badgeColor).toBe('error')
    expect(d.ariaLabel).toMatch(/failed/)
  })

  it('returns inconclusive on inconclusive', () => {
    const d = witnessIconDescriptor('inconclusive')
    expect(d.kind).toBe('inconclusive')
  })
})
