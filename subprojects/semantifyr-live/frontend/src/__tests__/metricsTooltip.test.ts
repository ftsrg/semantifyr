/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from 'vitest'
import {
  buildMetricsTooltip,
  isMeaningfulDuration,
} from '../lib/verification/metricsTooltip'

describe('isMeaningfulDuration', () => {
  it('rejects empty, missing and zero ISO durations', () => {
    expect(isMeaningfulDuration(undefined)).toBe(false)
    expect(isMeaningfulDuration(null)).toBe(false)
    expect(isMeaningfulDuration('')).toBe(false)
    expect(isMeaningfulDuration('PT0S')).toBe(false)
  })

  it('accepts non-zero durations', () => {
    expect(isMeaningfulDuration('PT0.5S')).toBe(true)
    expect(isMeaningfulDuration('PT1S')).toBe(true)
  })
})

describe('buildMetricsTooltip', () => {
  it('renders only the stages that have a meaningful duration', () => {
    const tip = buildMetricsTooltip({
      totalDuration: 'PT2.4S',
      preparationDuration: 'PT0.4S',
      verificationDuration: 'PT2S',
      backAnnotationDuration: 'PT0S',
    })
    expect(tip).toContain('Compilation:')
    expect(tip).toContain('Verification:')
    expect(tip).not.toContain('Back-annotation:')
  })

  it('appends portfolio and backend identifiers when supplied', () => {
    const tip = buildMetricsTooltip(
      { verificationDuration: 'PT1S' },
      { portfolioLabel: 'Smart', backendId: 'theta-cegar' },
    )
    expect(tip).toMatch(/Portfolio: Smart/)
    expect(tip).toMatch(/Backend: theta-cegar/)
  })

  it('still surfaces the portfolio when no metrics are present', () => {
    const tip = buildMetricsTooltip(undefined, { portfolioLabel: 'Smart' })
    expect(tip).toBe('Portfolio: Smart')
  })

  it('returns the empty string when there is genuinely nothing to say', () => {
    expect(buildMetricsTooltip(undefined)).toBe('')
    expect(buildMetricsTooltip({ totalDuration: 'PT0S' })).toBe('')
  })
})
