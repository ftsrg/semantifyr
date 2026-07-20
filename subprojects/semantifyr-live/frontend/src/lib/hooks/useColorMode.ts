/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { useCallback, useEffect, useState } from 'react';
import type { ColorModePreference, ResolvedColorMode } from '../util/colorMode';

const STORAGE_KEY = 'semantifyr-live-color-mode';

function readStoredPreference(): ColorModePreference | null {
  if (typeof window === 'undefined') return null;
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    if (stored === 'light' || stored === 'dark' || stored === 'system') return stored;
  } catch {
    /* localStorage may be blocked */
  }
  return null;
}

function resolveSystemTheme(): ResolvedColorMode {
  if (typeof window === 'undefined') return 'dark';
  return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
}

const CYCLE: readonly [ColorModePreference, ...ColorModePreference[]] = ['system', 'light', 'dark'];

export function useColorMode() {
  const [preference, setPreferenceState] = useState<ColorModePreference>(
    () => readStoredPreference() ?? 'system',
  );
  const [systemTheme, setSystemTheme] = useState<ResolvedColorMode>(() => resolveSystemTheme());

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const mql = window.matchMedia('(prefers-color-scheme: light)');
    const handler = (e: MediaQueryListEvent) => {
      setSystemTheme(e.matches ? 'light' : 'dark');
    };
    mql.addEventListener('change', handler);
    return () => { mql.removeEventListener('change', handler); };
  }, []);

  const colorMode: ResolvedColorMode = preference === 'system' ? systemTheme : preference;

  useEffect(() => {
    if (typeof document !== 'undefined') {
      document.documentElement.dataset.theme = colorMode;
      document.documentElement.dataset.themeChoice = preference;
    }
    try {
      window.localStorage.setItem(STORAGE_KEY, preference);
    } catch {
      /* ignore */
    }
  }, [colorMode, preference]);

  const setPreference = useCallback((p: ColorModePreference) => { setPreferenceState(p); }, []);

  const cycle = useCallback(() => {
    setPreferenceState((prev) => {
      const idx = CYCLE.indexOf(prev);
      return CYCLE[(idx + 1) % CYCLE.length] ?? CYCLE[0];
    });
  }, []);

  return { preference, colorMode, setPreference, cycle };
}
