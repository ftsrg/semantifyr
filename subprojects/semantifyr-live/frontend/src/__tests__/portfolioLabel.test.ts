/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from 'vitest'
import { findPortfolioLabel } from '../lib/verification/portfolioLabel'
import type { PortfolioInfo } from '../lib/api'

const PORTFOLIOS: PortfolioInfo[] = [
  { id: 'smart-full', displayName: 'Auto', description: '', available: true },
  { id: 'theta-full', displayName: 'Theta', description: '', available: true },
]

describe('findPortfolioLabel', () => {
  it('returns the display name when the id is registered', () => {
    expect(findPortfolioLabel(PORTFOLIOS, 'smart-full')).toBe('Auto')
    expect(findPortfolioLabel(PORTFOLIOS, 'theta-full')).toBe('Theta')
  })

  it('falls back to the raw id when the registry lacks an entry', () => {
    expect(findPortfolioLabel(PORTFOLIOS, 'unknown-id')).toBe('unknown-id')
  })

  it('returns undefined when the id is missing', () => {
    expect(findPortfolioLabel(PORTFOLIOS, undefined)).toBeUndefined()
    expect(findPortfolioLabel(PORTFOLIOS, '')).toBeUndefined()
  })

  it('returns the id even when the portfolios list is empty', () => {
    expect(findPortfolioLabel([], 'smart-full')).toBe('smart-full')
  })
})
