/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { PortfolioInfo } from '../api/types'

export function findPortfolioLabel(
  portfolios: readonly PortfolioInfo[],
  id: string | undefined,
): string | undefined {
  if (!id) {
    return undefined
  }
  return portfolios.find((p) => p.id === id)?.displayName ?? id
}
