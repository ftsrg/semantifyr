/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { PortfolioInfo } from '../api/types'

export interface PortfolioChoice {
  id: string
  name: string
  detail: string | null
}

export interface PortfolioChoices {
  // the portfolios over every engine, before the single-engine ones
  combined: PortfolioChoice[]
  singleEngine: PortfolioChoice[]
}

const COMBINED: Readonly<Record<string, { name: string, detail: string }>> = {
  'smart': { name: 'Automatic', detail: 'tries every engine at once and takes the first trusted answer' },
  'all-agree': { name: 'Cross-check', detail: 'runs every engine and requires them to agree' },
}

// a portfolio whose engine the server does not have is not offered
export function portfolioChoices(portfolios: readonly PortfolioInfo[]): PortfolioChoices {
  const available = portfolios.filter((portfolio) => {
    return portfolio.available
  })
  return {
    combined: available.flatMap((portfolio) => {
      const combined = COMBINED[portfolio.id]
      return combined ? [{ id: portfolio.id, name: combined.name, detail: combined.detail }] : []
    }),
    singleEngine: available
      .filter((portfolio) => {
        return COMBINED[portfolio.id] === undefined
      })
      .map((portfolio) => {
        return { id: portfolio.id, name: portfolio.displayName, detail: null }
      }),
  }
}

export function portfolioName(portfolios: readonly PortfolioInfo[], id: string | null | undefined): string {
  if (!id) {
    return 'Automatic'
  }
  return COMBINED[id]?.name ?? portfolios.find((portfolio) => {
    return portfolio.id === id
  })?.displayName ?? id
}
