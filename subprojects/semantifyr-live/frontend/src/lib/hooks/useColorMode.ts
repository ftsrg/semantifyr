/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { useCallback, useEffect } from 'react';
import { changeSettings, nextTheme, resolveColorMode, type Settings } from '../stores/settings';
import type { ReadableStore, Store } from '../stores/store';
import { useStore } from '../stores/useStore';
import type { ColorModePreference, ResolvedColorMode } from '../util/colorMode';

export interface ColorMode {
  preference: ColorModePreference;
  colorMode: ResolvedColorMode;
  cycle: () => void;
}

export function useColorMode(settings: Store<Settings>, system: ReadableStore<ResolvedColorMode>): ColorMode {
  const preference = useStore(settings, (current) => {
    return current.theme;
  });
  const colorMode = resolveColorMode(preference, useStore(system));

  useEffect(() => {
    document.documentElement.dataset.theme = colorMode;
    document.documentElement.dataset.themeChoice = preference;
  }, [colorMode, preference]);

  const cycle = useCallback(() => {
    changeSettings(settings, { theme: nextTheme(settings.get().theme) });
  }, [settings]);

  return { preference, colorMode, cycle };
}
