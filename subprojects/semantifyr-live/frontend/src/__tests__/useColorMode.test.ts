/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { useColorMode } from '../lib/hooks/useColorMode';

const STORAGE_KEY = 'semantifyr-live-color-mode';

interface FakeMql {
  matches: boolean;
  listeners: Set<(event: MediaQueryListEvent) => void>;
  addEventListener: (type: string, listener: (event: MediaQueryListEvent) => void) => void;
  removeEventListener: (type: string, listener: (event: MediaQueryListEvent) => void) => void;
}

function installMatchMedia(initiallyLight: boolean): FakeMql {
  const fake: FakeMql = {
    matches: initiallyLight,
    listeners: new Set(),
    addEventListener: (_type, listener) => { fake.listeners.add(listener); },
    removeEventListener: (_type, listener) => { fake.listeners.delete(listener); },
  };
  vi.stubGlobal('matchMedia', vi.fn(() => fake));
  return fake;
}

describe('useColorMode', () => {
  beforeEach(() => {
    window.localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
    document.documentElement.removeAttribute('data-theme-choice');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    window.localStorage.clear();
  });

  it('defaults to system preference and resolves it from matchMedia', () => {
    installMatchMedia(true);
    const { result } = renderHook(() => useColorMode());
    expect(result.current.preference).toBe('system');
    expect(result.current.colorMode).toBe('light');
    expect(document.documentElement.dataset.theme).toBe('light');
    expect(document.documentElement.dataset.themeChoice).toBe('system');
  });

  it('cycles system -> light -> dark -> system and persists each step', () => {
    installMatchMedia(true);
    const { result } = renderHook(() => useColorMode());
    act(() => { result.current.cycle(); });
    expect(result.current.preference).toBe('light');
    expect(window.localStorage.getItem(STORAGE_KEY)).toBe('light');

    act(() => { result.current.cycle(); });
    expect(result.current.preference).toBe('dark');
    expect(result.current.colorMode).toBe('dark');
    expect(window.localStorage.getItem(STORAGE_KEY)).toBe('dark');

    act(() => { result.current.cycle(); });
    expect(result.current.preference).toBe('system');
  });

  it('hydrates from a stored explicit preference and ignores matchMedia in that mode', () => {
    installMatchMedia(true);
    window.localStorage.setItem(STORAGE_KEY, 'dark');
    const { result } = renderHook(() => useColorMode());
    expect(result.current.preference).toBe('dark');
    expect(result.current.colorMode).toBe('dark');
  });

  it('updates the resolved theme when the OS preference flips while in system mode', () => {
    const mql = installMatchMedia(false);
    const { result } = renderHook(() => useColorMode());
    expect(result.current.colorMode).toBe('dark');

    act(() => {
      mql.matches = true;
      for (const listener of mql.listeners) {
        listener({ matches: true } as MediaQueryListEvent);
      }
    });
    expect(result.current.colorMode).toBe('light');
    expect(document.documentElement.dataset.theme).toBe('light');
  });

  it('explicit light/dark overrides the OS preference', () => {
    const mql = installMatchMedia(false);
    const { result } = renderHook(() => useColorMode());
    act(() => { result.current.setPreference('light'); });
    // OS flipping does not change the explicit override.
    act(() => {
      mql.matches = false;
      for (const listener of mql.listeners) {
        listener({ matches: false } as MediaQueryListEvent);
      }
    });
    expect(result.current.colorMode).toBe('light');
  });
});
