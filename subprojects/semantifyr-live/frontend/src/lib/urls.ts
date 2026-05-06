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

const COMPRESSED_PREFIX = 'g~';

function bytesToBase64Url(bytes: Uint8Array): string {
  let binary = '';
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]!);
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function base64UrlToBytes(s: string): Uint8Array {
  const padded = s.replace(/-/g, '+').replace(/_/g, '/');
  const pad = padded.length % 4 === 0 ? '' : '='.repeat(4 - (padded.length % 4));
  const binary = atob(padded + pad);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

async function readAllAsBytes(stream: ReadableStream<Uint8Array>): Promise<Uint8Array> {
  const chunks: Uint8Array[] = [];
  const reader = stream.getReader();
  let total = 0;
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    if (value) {
      chunks.push(value);
      total += value.length;
    }
  }
  const result = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    result.set(chunk, offset);
    offset += chunk.length;
  }
  return result;
}

function singleChunkStream(bytes: Uint8Array): ReadableStream<Uint8Array> {
  // Avoids `new Blob([bytes]).stream()`: jsdom (vitest) ships Blob without `stream()`. A
  // ReadableStream constructor is universally available wherever CompressionStream is.
  return new ReadableStream<Uint8Array>({
    start(controller) {
      controller.enqueue(bytes);
      controller.close();
    },
  });
}

/**
 * Encode a UTF-8 string as a {@code g~}-prefixed base64url-of-gzip. Empirically 6-12x smaller
 * than {@link encodeBase64Url} for OXSTS / Gamma source: keeps {@code Copy link} URLs comfortably
 * inside both the server's HTTP request-line limit and every modern browser's address-bar cap
 * even for the multi-thousand-line generated models. Falls back to plain base64url when the
 * platform lacks {@code CompressionStream} (or any of the required Streams primitives).
 */
export async function encodeCompressedBase64Url(s: string): Promise<string> {
  if (typeof CompressionStream === 'undefined' || typeof ReadableStream === 'undefined') {
    return encodeBase64Url(s);
  }
  try {
    const bytes = new TextEncoder().encode(s);
    // The TransformStream pair generics in lib.dom shifted between TS versions; cast to a
    // permissive stream type so we don't have to track every release. Runtime behaviour is
    // unaffected.
    const stream = singleChunkStream(bytes).pipeThrough(
      new CompressionStream('gzip') as unknown as ReadableWritablePair<Uint8Array, Uint8Array>,
    );
    const compressed = await readAllAsBytes(stream);
    return COMPRESSED_PREFIX + bytesToBase64Url(compressed);
  } catch {
    return encodeBase64Url(s);
  }
}

/**
 * Decode either a plain base64url payload (legacy {@code Copy link} URLs) or a
 * {@link encodeCompressedBase64Url} payload distinguished by the {@code g~} prefix. Returns
 * null if either decoding step fails so the caller can fall back to a default.
 */
export async function decodeMaybeCompressedBase64Url(s: string): Promise<string | null> {
  if (!s.startsWith(COMPRESSED_PREFIX)) {
    return decodeBase64Url(s);
  }
  if (typeof DecompressionStream === 'undefined' || typeof ReadableStream === 'undefined') {
    return null;
  }
  try {
    const bytes = base64UrlToBytes(s.slice(COMPRESSED_PREFIX.length));
    const stream = singleChunkStream(bytes).pipeThrough(
      new DecompressionStream('gzip') as unknown as ReadableWritablePair<Uint8Array, Uint8Array>,
    );
    const decompressed = await readAllAsBytes(stream);
    return new TextDecoder().decode(decompressed);
  } catch {
    return null;
  }
}
