/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from 'vitest'
import { witnessIconDescriptor } from '../lib/verification/witnessIcon'

describe('witnessIconDescriptor', () => {
  it('returns simple state when no validation has run', () => {
    const descriptor = witnessIconDescriptor(undefined)
    expect(descriptor.kind).toBe('simple')
    expect(descriptor.badgeVisible).toBe(false)
    expect(descriptor.ariaLabel).toBe('Show witness')
  })

  it('returns the quiet valid state on success', () => {
    const descriptor = witnessIconDescriptor('valid')
    expect(descriptor.kind).toBe('valid')
    expect(descriptor.badgeVisible).toBe(true)
    expect(descriptor.badgeColor).toBe('success')
    expect(descriptor.ariaLabel).toMatch(/passed/)
  })

  it('folds errored into the inconclusive bucket', () => {
    const descriptor = witnessIconDescriptor('errored')
    expect(descriptor.kind).toBe('inconclusive')
    expect(descriptor.badgeColor).toBe('warning')
  })

  it('returns the danger failed state on invalid', () => {
    const descriptor = witnessIconDescriptor('invalid')
    expect(descriptor.kind).toBe('failed')
    expect(descriptor.badgeColor).toBe('error')
    expect(descriptor.ariaLabel).toMatch(/failed/)
  })

  it('returns inconclusive on inconclusive', () => {
    const descriptor = witnessIconDescriptor('inconclusive')
    expect(descriptor.kind).toBe('inconclusive')
  })
})
