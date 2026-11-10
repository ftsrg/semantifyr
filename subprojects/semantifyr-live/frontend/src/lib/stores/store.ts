/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

export interface ReadableStore<T> {
  get: () => T
  subscribe: (listener: () => void) => () => void
}

export interface Store<T> extends ReadableStore<T> {
  set: (next: T) => void
  update: (updater: (previous: T) => T) => void
}

export function createStore<T>(initial: T): Store<T> {
  let state = initial
  const listeners = new Set<() => void>()

  const set = (next: T): void => {
    if (Object.is(next, state)) {
      return
    }
    state = next
    for (const listener of [...listeners]) {
      listener()
    }
  }

  return {
    get: () => {
      return state
    },
    set,
    update: (updater) => {
      set(updater(state))
    },
    subscribe: (listener) => {
      listeners.add(listener)
      return () => {
        listeners.delete(listener)
      }
    },
  }
}

export function constantStore<T>(value: T): ReadableStore<T> {
  return {
    get: () => {
      return value
    },
    subscribe: () => {
      return () => {}
    },
  }
}

// Tells its owner whether anyone listens, so a store that costs a request per change computes only while shown
export function createDemandStore<T>(initial: T, onDemand: (demanded: boolean) => void): Store<T> {
  const store = createStore(initial)
  let listeners = 0
  return {
    ...store,
    subscribe: (listener) => {
      const unsubscribe = store.subscribe(listener)
      listeners++
      if (listeners === 1) {
        onDemand(true)
      }
      let subscribed = true
      return () => {
        if (!subscribed) {
          return
        }
        subscribed = false
        unsubscribe()
        listeners--
        if (listeners === 0) {
          onDemand(false)
        }
      }
    },
  }
}
