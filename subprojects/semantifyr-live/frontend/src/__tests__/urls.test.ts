/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from 'vitest';
import {
  decodeCompressedBase64Url,
  encodeCompressedBase64Url,
  normalizeBaseUrl,
} from '../lib/api/urls';

describe('normalizeBaseUrl', () => {
  it('keeps an http base and derives the matching ws form', () => {
    expect(normalizeBaseUrl('http://localhost:18080')).toEqual({
      ws: 'ws://localhost:18080',
      http: 'http://localhost:18080',
    });
  });

  it('keeps an https base and derives the matching wss form', () => {
    expect(normalizeBaseUrl('https://live.semantifyr.org')).toEqual({
      ws: 'wss://live.semantifyr.org',
      http: 'https://live.semantifyr.org',
    });
  });

  it('strips a trailing slash', () => {
    expect(normalizeBaseUrl('https://live.semantifyr.org/')).toEqual({
      ws: 'wss://live.semantifyr.org',
      http: 'https://live.semantifyr.org',
    });
  });

  it('keeps an explicit ws scheme and derives http from it', () => {
    expect(normalizeBaseUrl('ws://localhost:18080')).toEqual({
      ws: 'ws://localhost:18080',
      http: 'http://localhost:18080',
    });
  });

  it('keeps an explicit wss scheme and derives https from it', () => {
    expect(normalizeBaseUrl('wss://live.semantifyr.org')).toEqual({
      ws: 'wss://live.semantifyr.org',
      http: 'https://live.semantifyr.org',
    });
  });

  it('defaults to wss/https when the scheme is omitted', () => {
    expect(normalizeBaseUrl('live.semantifyr.org')).toEqual({
      ws: 'wss://live.semantifyr.org',
      http: 'https://live.semantifyr.org',
    });
  });
});

describe('encodeCompressedBase64Url / decodeCompressedBase64Url', () => {
  it('round-trips ASCII', async () => {
    const text = 'package demo\nclass Main { var x: int := 0 }';
    const encoded = await encodeCompressedBase64Url(text);
    expect(await decodeCompressedBase64Url(encoded)).toBe(text);
  });

  it('round-trips multi-byte UTF-8', async () => {
    const text = '/* émoji 🎉 mix */ var ç := "λ"';
    const encoded = await encodeCompressedBase64Url(text);
    expect(await decodeCompressedBase64Url(encoded)).toBe(text);
  });

  it('uses URL-safe alphabet (no +, /, =)', async () => {
    const encoded = await encodeCompressedBase64Url('?>&%/+ test '.repeat(20));
    expect(encoded).not.toMatch(/[+/=]/);
  });

  it('returns null when the payload is malformed', async () => {
    expect(await decodeCompressedBase64Url('!!!not-valid!!!')).toBeNull();
  });

  it('returns null when the payload is not gzip', async () => {
    // valid base64url but not gzipped bytes
    expect(await decodeCompressedBase64Url('cGFja2FnZSBkZW1v')).toBeNull();
  });

  it('round-trips an empty string', async () => {
    const encoded = await encodeCompressedBase64Url('');
    expect(await decodeCompressedBase64Url(encoded)).toBe('');
  });
});
