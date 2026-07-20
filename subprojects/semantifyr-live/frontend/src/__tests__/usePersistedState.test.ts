/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import {
  boolCodec,
  sizeCodec,
  stringCodec,
  usePersistedState,
} from '../lib/hooks/usePersistedState';

const KEY = 'usePersistedState-test-key';

describe('usePersistedState', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });
  afterEach(() => {
    window.localStorage.clear();
  });

  it('returns the fallback when nothing is stored and writes through on update', () => {
    const { result } = renderHook(() => usePersistedState(KEY, 'fallback', stringCodec));
    expect(result.current[0]).toBe('fallback');

    act(() => {
      result.current[1]('updated');
    });
    expect(result.current[0]).toBe('updated');
    expect(window.localStorage.getItem(KEY)).toBe('updated');
  });

  it('hydrates from localStorage on first render when a value is already stored', () => {
    window.localStorage.setItem(KEY, 'pre-existing');
    const { result } = renderHook(() => usePersistedState(KEY, 'fallback', stringCodec));
    expect(result.current[0]).toBe('pre-existing');
  });

  it('honours the boolCodec round-trip', () => {
    const { result } = renderHook(() => usePersistedState(KEY, true, boolCodec));
    expect(result.current[0]).toBe(true);
    act(() => {
      result.current[1](false);
    });
    expect(result.current[0]).toBe(false);
    expect(window.localStorage.getItem(KEY)).toBe('false');
  });

  it('rounds floats through sizeCodec on write', () => {
    const { result } = renderHook(() => usePersistedState(KEY, 100, sizeCodec));
    act(() => {
      result.current[1](321.7);
    });
    expect(result.current[0]).toBe(321.7);
    expect(window.localStorage.getItem(KEY)).toBe('322');
  });

  it('falls back when the stored bool is malformed', () => {
    window.localStorage.setItem(KEY, 'maybe');
    const { result } = renderHook(() => usePersistedState(KEY, true, boolCodec));
    expect(result.current[0]).toBe(true);
  });

  it('falls back when the stored size is non-positive', () => {
    window.localStorage.setItem(KEY, '0');
    const { result } = renderHook(() => usePersistedState(KEY, 480, sizeCodec));
    expect(result.current[0]).toBe(480);
  });
});
