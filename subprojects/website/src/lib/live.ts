/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import type { FlavorInfo, FlavorsResponse, PortfoliosResponse } from '@semantifyr/editor-common';

export interface LiveUrls {
  ws: string;
  http: string;
}

export function normalizeBaseUrl(url: string): LiveUrls {
  const base = url.trim().replace(/\/$/, '');
  if (base.startsWith('http://')) return { ws: 'ws://' + base.slice('http://'.length), http: base };
  if (base.startsWith('https://')) return { ws: 'wss://' + base.slice('https://'.length), http: base };
  if (base.startsWith('ws://')) return { ws: base, http: 'http://' + base.slice('ws://'.length) };
  if (base.startsWith('wss://')) return { ws: base, http: 'https://' + base.slice('wss://'.length) };
  return { ws: 'wss://' + base, http: 'https://' + base };
}

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

// Live decodes ?code= as base64url(gzip(text)).
async function encodeCompressedBase64Url(text: string): Promise<string> {
  const compressed = new Blob([text]).stream().pipeThrough(new CompressionStream('gzip'));
  const bytes = new Uint8Array(await new Response(compressed).arrayBuffer());
  let binary = '';
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export async function liveEditorUrl(
  liveBackendUrl: string,
  flavor: string,
  code: string,
  embed = false,
): Promise<string> {
  const params = new URLSearchParams();
  if (embed) {
    params.set('embed', '1');
  }
  params.set('mode', flavor);
  params.set('code', await encodeCompressedBase64Url(code));
  return `${normalizeBaseUrl(liveBackendUrl).http}/?${params.toString()}`;
}
