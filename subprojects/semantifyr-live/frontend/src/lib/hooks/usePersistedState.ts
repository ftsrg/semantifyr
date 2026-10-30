/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { useCallback, useState } from 'react';
import {
  persistBool,
  persistSize,
  persistString,
  readPersistedBool,
  readPersistedSize,
  readPersistedString,
} from '../util/persistence';

export interface PersistedCodec<T> {
  read: (key: string, fallback: T) => T;
  write: (key: string, value: T) => void;
}

export const stringCodec: PersistedCodec<string> = {
  read: readPersistedString,
  write: persistString,
};

export const boolCodec: PersistedCodec<boolean> = {
  read: readPersistedBool,
  write: persistBool,
};

export const sizeCodec: PersistedCodec<number> = {
  read: readPersistedSize,
  write: persistSize,
};

export function usePersistedState<T>(
  key: string,
  fallback: T,
  codec: PersistedCodec<T>,
): [T, (next: T) => void] {
  const [value, setValue] = useState<T>(() => codec.read(key, fallback));
  const setAndPersist = useCallback(
    (next: T) => {
      setValue(next);
      codec.write(key, next);
    },
    [key, codec],
  );
  return [value, setAndPersist];
}
