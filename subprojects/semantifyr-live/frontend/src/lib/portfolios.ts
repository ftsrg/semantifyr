/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

export interface PortfolioInfo {
  id: string;
  displayName: string;
  description: string;
  available: boolean;
}

interface PortfoliosResponse {
  portfolios: PortfolioInfo[];
}

export async function fetchPortfolios(httpBase: string): Promise<PortfolioInfo[]> {
  const response = await fetch(`${httpBase}/api/portfolios`);
  if (!response.ok) return [];
  const data = (await response.json()) as PortfoliosResponse;
  return data.portfolios;
}
