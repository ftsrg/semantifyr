/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from 'vitest';
import { decodeBase64Url, encodeBase64Url, normalizeBaseUrl } from '../lib/urls';

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

describe('encodeBase64Url / decodeBase64Url', () => {
  it('round-trips ASCII', () => {
    const text = 'package demo\nclass Main { var x: int := 0 }';
    expect(decodeBase64Url(encodeBase64Url(text))).toBe(text);
  });

  it('round-trips multi-byte UTF-8', () => {
    const text = '/* émoji 🎉 mix */ var ç := "λ"';
    expect(decodeBase64Url(encodeBase64Url(text))).toBe(text);
  });

  it('uses URL-safe alphabet (no +, /, =)', () => {
    const encoded = encodeBase64Url('?>&%/+ test '.repeat(20));
    expect(encoded).not.toMatch(/[+/=]/);
  });

  it('returns null for malformed base64url input', () => {
    expect(decodeBase64Url('!!!not-valid!!!')).toBeNull();
  });

  it('round-trips an empty string', () => {
    expect(decodeBase64Url(encodeBase64Url(''))).toBe('');
  });
});
