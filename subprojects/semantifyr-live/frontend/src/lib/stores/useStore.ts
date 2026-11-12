/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { useSyncExternalStore } from 'react'
import type { ReadableStore } from './store'

// A selector returns a stored reference or a primitive, never a fresh object, or every change re-renders
export function useStore<T>(store: ReadableStore<T>): T
export function useStore<T, S>(store: ReadableStore<T>, select: (state: T) => S): S
export function useStore<T, S>(store: ReadableStore<T>, select?: (state: T) => S): T | S {
  return useSyncExternalStore(store.subscribe, () => {
    return select ? select(store.get()) : store.get()
  })
}
