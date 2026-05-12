/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

/**
 * Bounded-attempt reconnect state machine, lifted out of the React tree so we can drive it
 * from a unit test instead of from a Monaco / WebSocket integration.
 *
 * Lifecycle:
 *
 * - {@link schedule} arms a reconnect with the next backoff entry; reports
 *   `reconnecting (n/max)` via {@link onStatus}; on success resets the attempt counter; on
 *   failure recurses into the next attempt.
 * - {@link cancel} clears any pending timer and hands control back to the caller (the user
 *   pressed Disconnect, or the component is unmounting).
 * - {@link resetAttempts} forces the counter back to zero, used when a manual reconnect
 *   succeeds and we want the next drop to count from one again.
 *
 * The controller does not own connection state itself; the caller drives {@link schedule}
 * after a transport drop and resets attempts on a clean connect. This keeps the
 * controller transport-agnostic and trivially testable.
 */

export type ReconnectStatus = 'reconnecting' | 'errored'

export interface ReconnectControllerOptions {
  /** Hard cap on attempts before we give up and report `errored`. */
  maxAttempts: number
  /**
   * Per-attempt delay in ms. The Nth attempt reads index N-1 (1-indexed); attempts past the
   * end of this array reuse the last entry.
   */
  backoffMs: readonly number[]
  /** Performs the connect; resolves on success, rejects on failure. */
  connect: () => Promise<void>
  /**
   * True if the wider component has been torn down and any pending reconnect should be a
   * no-op. Read on every tick so a late callback that fires after the cleanup never lands.
   */
  isCancelled: () => boolean
  onStatus: (status: ReconnectStatus, info?: string) => void
}

export interface ReconnectController {
  /** Arm the next reconnect attempt. No-op if {@link isCancelled} returns true. */
  schedule(): void
  /** Cancel any in-flight timer; subsequent {@link schedule} calls start fresh. */
  cancel(): void
  /** Reset the attempt counter (call after a successful manual reconnect). */
  resetAttempts(): void
  /** Inspect the current attempt count; primarily for tests. */
  attempts(): number
}

interface InternalState {
  attempts: number
  timer: ReturnType<typeof setTimeout> | null
}

function delayFor(backoffMs: readonly number[], attempt: number): number {
  // attempt is 1-indexed for the user; the array is 0-indexed.
  return backoffMs[attempt - 1] ?? backoffMs[backoffMs.length - 1] ?? 0
}

export function createReconnectController(options: ReconnectControllerOptions): ReconnectController {
  const state: InternalState = { attempts: 0, timer: null }

  const clearTimer = (): void => {
    if (state.timer !== null) {
      clearTimeout(state.timer)
      state.timer = null
    }
  }

  const schedule = (): void => {
    if (options.isCancelled()) {
      return
    }
    clearTimer()
    const next = state.attempts + 1
    if (next > options.maxAttempts) {
      options.onStatus('errored', 'Connection failed after multiple attempts')
      return
    }
    state.attempts = next
    options.onStatus('reconnecting', `Reconnecting (attempt ${next}/${options.maxAttempts})...`)
    state.timer = setTimeout(async () => {
      state.timer = null
      try {
        await options.connect()
        if (options.isCancelled()) {
          return
        }
        state.attempts = 0
      } catch {
        if (options.isCancelled()) {
          return
        }
        schedule()
      }
    }, delayFor(options.backoffMs, next))
  }

  return {
    schedule,
    cancel: () => {
      clearTimer()
    },
    resetAttempts: () => {
      state.attempts = 0
    },
    attempts: () => state.attempts,
  }
}
