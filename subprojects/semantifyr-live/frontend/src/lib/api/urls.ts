/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

export interface LiveServerUrls {
  ws: string
  http: string
}

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
  for (const b of bytes) {
    binary += String.fromCharCode(b)
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
  for (;;) {
    const { value, done } = await reader.read()
    if (done) {
      break
    }
    chunks.push(value)
    total += value.length
  }
  const result = new Uint8Array(total)
  let offset = 0
  for (const chunk of chunks) {
    result.set(chunk, offset)
    offset += chunk.length
  }
  return result
}

// Avoids `new Blob([bytes]).stream()`; jsdom's Blob doesn't ship `stream()`.
function singleChunkStream(bytes: Uint8Array): ReadableStream<Uint8Array> {
  return new ReadableStream<Uint8Array>({
    start(controller) {
      controller.enqueue(bytes)
      controller.close()
    },
  })
}

// base64url(gzip(s)); 6-12x smaller than plain base64url on OXSTS / Gamma source.
export async function encodeCompressedBase64Url(s: string): Promise<string> {
  const bytes = new TextEncoder().encode(s)
  const stream = singleChunkStream(bytes).pipeThrough(
    new CompressionStream('gzip') as unknown as ReadableWritablePair<Uint8Array, Uint8Array>,
  )
  const compressed = await readAllAsBytes(stream)
  return bytesToBase64Url(compressed)
}

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
