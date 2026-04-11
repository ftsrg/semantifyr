/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

export interface LiveServerUrls {
  ws: string;
  http: string;
}

/** Normalise the user-supplied liveServerUrl into both ws:// and http:// base URLs. */
export function normalizeBaseUrl(url: string): LiveServerUrls {
  const base = url.trim().replace(/\/$/, '');
  if (base.startsWith('http://')) return { ws: 'ws://' + base.slice('http://'.length), http: base };
  if (base.startsWith('https://')) return { ws: 'wss://' + base.slice('https://'.length), http: base };
  if (base.startsWith('ws://')) return { ws: base, http: 'http://' + base.slice('ws://'.length) };
  if (base.startsWith('wss://')) return { ws: base, http: 'https://' + base.slice('wss://'.length) };
  return { ws: 'wss://' + base, http: 'https://' + base };
}

/** Encode a UTF-8 string as base64url for safe inclusion in a URL query parameter. */
export function encodeBase64Url(s: string): string {
  if (typeof btoa === 'undefined') return s;
  const b64 = btoa(
    encodeURIComponent(s).replace(/%([0-9A-F]{2})/g, (_, hex) => String.fromCharCode(parseInt(hex, 16))),
  );
  return b64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function decodeBase64Url(s: string): string | null {
  if (typeof atob === 'undefined') return null;
  try {
    const padded = s.replace(/-/g, '+').replace(/_/g, '/');
    const pad = padded.length % 4 === 0 ? '' : '='.repeat(4 - (padded.length % 4));
    return decodeURIComponent(
      atob(padded + pad)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join(''),
    );
  } catch {
    return null;
  }
}
