/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { useEffect, useState } from 'react';

function readOnline(): boolean {
  if (typeof navigator === 'undefined') {
    return true;
  }
  return navigator.onLine;
}

// navigator.onLine === false reliably means offline; true is a best-effort hint.
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
    update();
    return () => {
      window.removeEventListener('online', update);
      window.removeEventListener('offline', update);
    };
  }, []);

  return online;
}
