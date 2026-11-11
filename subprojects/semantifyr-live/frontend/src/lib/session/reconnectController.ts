/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

export type ReconnectStatus =
  | 'reconnecting'
  | 'connected'
  | 'terminated'

export interface SocketClose {
  code: number
  reason: string
}

export const CloseCode = {
  SessionTerminated: 4000,
  MissingFlavor: 4400,
  UnknownFlavor: 4404,
  TooManySessions: 4429,
} as const

const TERMINAL_CLOSE_CODES: ReadonlySet<number> = new Set([
  CloseCode.SessionTerminated,
  CloseCode.MissingFlavor,
  CloseCode.UnknownFlavor,
])

export function isTerminalClose(close: SocketClose | null): close is SocketClose {
  return close !== null && TERMINAL_CLOSE_CODES.has(close.code)
}

export function sessionLimitReason(close: SocketClose | null): string | null {
  if (close?.code !== CloseCode.TooManySessions) {
    return null
  }
  return close.reason || 'Too many sessions'
}

// Wraps the onclose handler the language client stops on, so the close is known when the client stops; Chromium runs that handler before a capturing listener
export function observeSocketClose(socket: WebSocket, onClose: (close: SocketClose) => void): void {
  const stop = socket.onclose
  socket.onclose = (event) => {
    onClose({ code: event.code, reason: event.reason })
    stop?.call(socket, event)
  }
}

// a session that did not start in time, which the attempt that follows names as its reason
export class SessionStartTimeout extends Error {}

function reasonOf(error: unknown): string | undefined {
  return error instanceof SessionStartTimeout ? error.message : undefined
}

export interface ReconnectControllerOptions {
  // Nth attempt waits entry N-1; attempts past the end wait the last entry, so the wait grows to a cap and the attempts never end
  backoffMs: readonly number[]
  connect: () => Promise<void>
  isCancelled: () => boolean
  lastClose: () => SocketClose | null
  onStatus: (status: ReconnectStatus, info?: ReconnectInfo) => void
  now?: () => number
}

export interface ReconnectInfo {
  reason: string
  // the server is at capacity and refused the session
  refused: boolean
  // when the next attempt starts, null for a session the server ended
  retryAt: number | null
}

export interface ReconnectController {
  schedule(error?: unknown): void
  cancel(): void
  resetAttempts(): void
  attempts(): number
  terminated(): boolean
}

interface InternalState {
  attempts: number
  timer: ReturnType<typeof setTimeout> | null
  terminated: boolean
}

function delayFor(backoffMs: readonly number[], attempt: number): number {
  return backoffMs[attempt - 1] ?? backoffMs[backoffMs.length - 1] ?? 0
}

export function createReconnectController(options: ReconnectControllerOptions): ReconnectController {
  const state: InternalState = { attempts: 0, timer: null, terminated: false }
  const now = options.now ?? Date.now

  const clearTimer = (): void => {
    if (state.timer !== null) {
      clearTimeout(state.timer)
      state.timer = null
    }
  }

  const schedule = (error?: unknown): void => {
    if (options.isCancelled() || state.terminated) {
      return
    }
    clearTimer()
    const close = options.lastClose()
    if (isTerminalClose(close)) {
      state.terminated = true
      options.onStatus('terminated', { reason: close.reason || 'The server closed the session', refused: false, retryAt: null })
      return
    }
    const rejection = sessionLimitReason(close)
    state.attempts += 1
    const delay = delayFor(options.backoffMs, state.attempts)
    options.onStatus('reconnecting', { reason: rejection ?? reasonOf(error) ?? 'Connection lost', refused: rejection !== null, retryAt: now() + delay })
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
        } catch (failure) {
          if (options.isCancelled()) {
            return
          }
          schedule(failure)
        }
      }
      void run()
    }, delay)
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
    terminated: () => state.terminated,
  }
}
