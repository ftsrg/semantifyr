/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { PortfolioInfo } from '../api/types'

/**
 * Resolve a portfolio (or fallback backend) id to its user-facing display name. Returns the
 * raw id if no entry matches - happens during the brief window between {@code /api/portfolios}
 * resolving and the React state landing, or for ad-hoc backend ids the demo's portfolio
 * registry doesn't list. Returns {@code undefined} only when the input id itself is missing.
 */
export function findPortfolioLabel(
  portfolios: readonly PortfolioInfo[],
  id: string | undefined,
): string | undefined {
  if (!id) {
    return undefined
  }
  return portfolios.find((p) => p.id === id)?.displayName ?? id
}
