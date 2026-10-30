/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

export type ReconnectStatus = 'reconnecting' | 'connected' | 'errored'

export interface ReconnectControllerOptions {
  maxAttempts: number
  // Nth attempt uses index N-1; attempts past the end reuse the last entry.
  backoffMs: readonly number[]
  connect: () => Promise<void>
  isCancelled: () => boolean
  onStatus: (status: ReconnectStatus, info?: string) => void
}

export interface ReconnectController {
  schedule(): void
  cancel(): void
  resetAttempts(): void
  attempts(): number
}

interface InternalState {
  attempts: number
  timer: ReturnType<typeof setTimeout> | null
}

function delayFor(backoffMs: readonly number[], attempt: number): number {
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
    state.timer = setTimeout(() => {
      state.timer = null
      const run = async (): Promise<void> => {
        try {
          await options.connect()
          if (options.isCancelled()) {
            return
          }
          state.attempts = 0
          options.onStatus('connected')
        } catch {
          if (options.isCancelled()) {
            return
          }
          schedule()
        }
      }
      void run()
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
