/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Shared helpers for the lightweight CodeBlock addons (`VerifyButton`, `OpenInLiveLink`).
 *
 * Nothing here pulls in monaco-editor or @codingame/* — those live in the standalone
 * `semantifyr-live-frontend` SPA. The website only needs to (a) shell out to the live
 * backend's HTTP/WS surface for the verify-this flow and (b) build "Open in live editor"
 * links pointing at the deployed frontend.
 */

export interface BackendUrls {
  ws: string;
  http: string;
}

/**
 * Normalise the user-supplied backend URL into both ws:// and http:// base URLs.
 *
 * The website is configured with a single `liveBackendUrl` value (an http(s) URL), but
 * the verify flow needs the WebSocket form too. Stripping a trailing `/` keeps the
 * downstream concatenation `${base}/path` clean.
 */
export function normalizeBaseUrl(url: string): BackendUrls {
  const base = url.trim().replace(/\/$/, '');
  if (base.startsWith('http://')) return { ws: 'ws://' + base.slice('http://'.length), http: base };
  if (base.startsWith('https://')) return { ws: 'wss://' + base.slice('https://'.length), http: base };
  if (base.startsWith('ws://')) return { ws: base, http: 'http://' + base.slice('ws://'.length) };
  if (base.startsWith('wss://')) return { ws: base, http: 'https://' + base.slice('wss://'.length) };
  return { ws: 'wss://' + base, http: 'https://' + base };
}

export interface FlavorInfo {
  id: string;
  displayName: string;
  languageId: string;
  fileName: string;
  verify: boolean;
  verifyCommand: string | null;
}

export interface FlavorsResponse {
  flavors: FlavorInfo[];
}

/** Fetch the verify capability + LSP wiring for a single flavor from the live backend. */
export async function fetchFlavor(httpBase: string, language: string): Promise<FlavorInfo | null> {
  const r = await fetch(`${httpBase}/api/flavors`);
  if (!r.ok) return null;
  const data = (await r.json()) as FlavorsResponse;
  return data.flavors.find((f) => f.id === language) ?? null;
}

/** Encode a UTF-8 string as base64url for safe inclusion in a URL query parameter. */
export function encodeBase64Url(s: string): string {
  if (typeof btoa === 'undefined') return s;
  const b64 = btoa(
    encodeURIComponent(s).replace(/%([0-9A-F]{2})/g, (_, hex) => String.fromCharCode(parseInt(hex, 16))),
  );
  return b64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
