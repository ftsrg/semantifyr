/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { portfolioNames } from '@semantifyr/editor-common/terms'

import { DEFAULT_PORTFOLIO_NAME } from '../../texts'
import type { PortfolioInfo } from '../api/types'

export interface PortfolioChoice {
  id: string
  name: string
  detail: string | null
}

export interface PortfolioChoices {
  // the portfolios over every backend, before the single-backend ones
  combined: PortfolioChoice[]
  singleBackend: PortfolioChoice[]
}

const COMBINED: ReadonlySet<string> = new Set([
  'smart',
  'all-agree',
])

function choiceOf(portfolio: PortfolioInfo): PortfolioChoice {
  return { id: portfolio.id, name: portfolio.displayName, detail: portfolio.description === '' ? null : portfolio.description }
}

// a portfolio whose backend the server does not have is not offered
export function portfolioChoices(portfolios: readonly PortfolioInfo[]): PortfolioChoices {
  const available = portfolios.filter((portfolio) => {
    return portfolio.available
  })
  return {
    combined: available
      .filter((portfolio) => {
        return COMBINED.has(portfolio.id)
      })
      .map(choiceOf),
    singleBackend: available
      .filter((portfolio) => {
        return !COMBINED.has(portfolio.id)
      })
      .map(choiceOf),
  }
}

export function portfolioName(portfolios: readonly PortfolioInfo[], id: string | null | undefined): string {
  if (!id) {
    return DEFAULT_PORTFOLIO_NAME
  }
  return portfolios.find((portfolio) => {
    return portfolio.id === id
  })?.displayName ?? portfolioNames[id] ?? id
}
