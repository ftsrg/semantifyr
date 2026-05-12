/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { useEffect, useState } from 'react';

function readOnline(): boolean {
  // `navigator.onLine === false` is the only reliable signal; absence of the API (SSR, some
  // test environments) is treated as "online" so we never block on a value we can't read.
  if (typeof navigator === 'undefined') {
    return true;
  }
  return navigator.onLine !== false;
}

/**
 * Tracks the browser's online/offline state via the {@code online} / {@code offline} window
 * events. Falls back to {@code true} when {@code navigator} is unavailable. Note `onLine` is a
 * best-effort hint: `false` reliably means offline, `true` does not guarantee reachability.
 */
export function useOnlineStatus(): boolean {
  const [online, setOnline] = useState<boolean>(() => readOnline());

  useEffect(() => {
    if (typeof window === 'undefined') {
      return;
    }
    const update = (): void => {
      setOnline(readOnline());
    };
    window.addEventListener('online', update);
    window.addEventListener('offline', update);
    // Re-read once on mount in case the value changed between the initial state and the effect.
    update();
    return () => {
      window.removeEventListener('online', update);
      window.removeEventListener('offline', update);
    };
  }, []);

  return online;
}
