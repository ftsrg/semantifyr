/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

export interface LiveServerUrls {
  ws: string
  http: string
}

/** Normalise the user-supplied liveServerUrl into both ws:// and http:// base URLs. */
export function normalizeBaseUrl(url: string): LiveServerUrls {
  const base = url.trim().replace(/\/$/, '')
  if (base.startsWith('http://')) {
    return { ws: 'ws://' + base.slice('http://'.length), http: base }
  }
  if (base.startsWith('https://')) {
    return { ws: 'wss://' + base.slice('https://'.length), http: base }
  }
  if (base.startsWith('ws://')) {
    return { ws: base, http: 'http://' + base.slice('ws://'.length) }
  }
  if (base.startsWith('wss://')) {
    return { ws: base, http: 'https://' + base.slice('wss://'.length) }
  }
  return { ws: 'wss://' + base, http: 'https://' + base }
}

function bytesToBase64Url(bytes: Uint8Array): string {
  let binary = ''
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]!)
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function base64UrlToBytes(s: string): Uint8Array {
  const padded = s.replace(/-/g, '+').replace(/_/g, '/')
  const pad = padded.length % 4 === 0 ? '' : '='.repeat(4 - (padded.length % 4))
  const binary = atob(padded + pad)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes
}

async function readAllAsBytes(stream: ReadableStream<Uint8Array>): Promise<Uint8Array> {
  const chunks: Uint8Array[] = []
  const reader = stream.getReader()
  let total = 0
  while (true) {
    const { value, done } = await reader.read()
    if (done) {
      break
    }
    if (value) {
      chunks.push(value)
      total += value.length
    }
  }
  const result = new Uint8Array(total)
  let offset = 0
  for (const chunk of chunks) {
    result.set(chunk, offset)
    offset += chunk.length
  }
  return result
}

function singleChunkStream(bytes: Uint8Array): ReadableStream<Uint8Array> {
  // Avoids `new Blob([bytes]).stream()`: jsdom (vitest) ships Blob without `stream()`. A
  // ReadableStream constructor is universally available wherever CompressionStream is.
  return new ReadableStream<Uint8Array>({
    start(controller) {
      controller.enqueue(bytes)
      controller.close()
    },
  })
}

/**
 * Encode a UTF-8 string as base64url-of-gzip. Empirically 6-12x smaller than plain base64url
 * for OXSTS / Gamma source: keeps {@code Copy link} URLs comfortably inside both the server's
 * HTTP request-line limit and every modern browser's address-bar cap even for the
 * multi-thousand-line generated models.
 */
export async function encodeCompressedBase64Url(s: string): Promise<string> {
  const bytes = new TextEncoder().encode(s)
  const stream = singleChunkStream(bytes).pipeThrough(
    new CompressionStream('gzip') as unknown as ReadableWritablePair<Uint8Array, Uint8Array>,
  )
  const compressed = await readAllAsBytes(stream)
  return bytesToBase64Url(compressed)
}

/**
 * Decode the {@link encodeCompressedBase64Url} payload. Returns null if the input is malformed
 * or not a valid gzip / base64url payload so the caller can fall back to a default (e.g. the
 * example's bundled source).
 */
export async function decodeCompressedBase64Url(s: string): Promise<string | null> {
  try {
    const bytes = base64UrlToBytes(s)
    const stream = singleChunkStream(bytes).pipeThrough(
      new DecompressionStream('gzip') as unknown as ReadableWritablePair<Uint8Array, Uint8Array>,
    )
    const decompressed = await readAllAsBytes(stream)
    return new TextDecoder().decode(decompressed)
  } catch {
    return null
  }
}
