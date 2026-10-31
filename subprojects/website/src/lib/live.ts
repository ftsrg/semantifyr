/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import useDocusaurusContext from '@docusaurus/useDocusaurusContext';

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

// Only /api/health answers cross-origin requests.
export async function isLiveServerUp(liveBackendUrl: string): Promise<boolean> {
  try {
    const response = await fetch(`${normalizeBaseUrl(liveBackendUrl).http}/api/health`);
    return response.ok;
  } catch {
    return false;
  }
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
