/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import { normalizeBaseUrl, type FlavorInfo, type FlavorsResponse, type PortfoliosResponse } from '@semantifyr/editor-common';

export function useLiveBackendUrl(): string {
  const { siteConfig } = useDocusaurusContext();
  return siteConfig.customFields?.liveBackendUrl as string;
}

export interface LiveSetup {
  flavors: FlavorInfo[];
  defaultPortfolio: string;
}

async function fetchJson<T>(url: string): Promise<T> {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`${url} answered ${response.status}`);
  }
  return (await response.json()) as T;
}

// Rejects when the live server is unreachable.
export async function fetchLiveSetup(liveBackendUrl: string): Promise<LiveSetup> {
  const base = normalizeBaseUrl(liveBackendUrl).http;
  const [flavors, portfolios] = await Promise.all([
    fetchJson<FlavorsResponse>(`${base}/api/flavors`),
    fetchJson<PortfoliosResponse>(`${base}/api/portfolios`),
  ]);
  return { flavors: flavors.flavors, defaultPortfolio: portfolios.defaultPortfolio };
}
