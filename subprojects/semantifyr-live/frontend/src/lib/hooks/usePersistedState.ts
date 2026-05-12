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

/**
 * Codec for one persisted key: how to materialise the stored bytes back into {@code T} (with a
 * fallback when nothing is stored or the value is malformed) and how to write {@code T} back
 * out. The bool / size / string variants below cover today's usage; bring your own codec when
 * the value shape grows.
 */
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

/**
 * Stateful hook backed by {@code localStorage}: returns a tuple identical in shape to
 * {@code useState}, but every setter writes through to the configured codec. SSR / blocked
 * storage / malformed stored values all fall back to {@code fallback} per the underlying
 * helpers' contract.
 */
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
