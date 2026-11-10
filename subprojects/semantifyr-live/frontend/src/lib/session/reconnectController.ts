/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

export type ReconnectStatus =
  | 'reconnecting'
  | 'connected'
  | 'errored'
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

export interface ReconnectControllerOptions {
  maxAttempts: number
  // Nth attempt uses index N-1; attempts past the end reuse the last entry.
  backoffMs: readonly number[]
  connect: () => Promise<void>
  isCancelled: () => boolean
  lastClose: () => SocketClose | null
  onStatus: (status: ReconnectStatus, info?: string) => void
}

export interface ReconnectController {
  schedule(): void
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

  const clearTimer = (): void => {
    if (state.timer !== null) {
      clearTimeout(state.timer)
      state.timer = null
    }
  }

  const schedule = (): void => {
    if (options.isCancelled() || state.terminated) {
      return
    }
    clearTimer()
    const close = options.lastClose()
    if (isTerminalClose(close)) {
      state.terminated = true
      options.onStatus('terminated', close.reason || 'The server closed the session')
      return
    }
    const rejection = sessionLimitReason(close)
    const next = state.attempts + 1
    if (next > options.maxAttempts) {
      options.onStatus('errored', rejection ?? 'Connection failed after multiple attempts')
      return
    }
    state.attempts = next
    const attemptText = `attempt ${next}/${options.maxAttempts}`
    options.onStatus(
      'reconnecting',
      rejection !== null ? `${rejection}, reconnecting (${attemptText})...` : `Reconnecting (${attemptText})...`,
    )
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
    terminated: () => state.terminated,
  }
}
