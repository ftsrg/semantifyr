/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { LanguageClientCallbacks } from '../lib/session/monaco'
import { CloseCode } from '../lib/session/reconnectController'

const connections = vi.hoisted(() => {
  return {
    callbacks: [] as LanguageClientCallbacks[],
  }
})

vi.mock('vscode', () => {
  return {}
})

vi.mock('../lib/session/monaco', () => {
  return {
    applyColorTheme: async () => {},
    createFileUri: (fileName: string) => {
      return {
        toString: () => `file:///workspace/${fileName}`,
      }
    },
    createEditor: async () => {
      return {
        editorApp: {
          getEditor: () => undefined,
          dispose: async () => {},
        },
        fsOverlay: {
          dispose: () => {},
        },
      }
    },
    createSecondaryEditor: async () => {
      return {
        dispose: () => {},
      }
    },
    connectWorkerLanguageClient: async () => {
      throw new Error('no worker in this test')
    },
    connectLanguageClient: async (
      _backendUrl: string,
      _flavorId: string,
      _languageId: string,
      callbacks: LanguageClientCallbacks,
    ) => {
      connections.callbacks.push(callbacks)
      return {
        getLanguageClient: () => undefined,
        dispose: async () => {},
      }
    },
  }
})

vi.mock('../lib/api/rest', () => {
  return {
    createApi: () => {
      return {
        fetchFlavor: async () => null,
      }
    },
  }
})

const { createLiveEditorSession } = await import('../lib/session/liveEditorSession')

async function startSession(): Promise<{
  session: ReturnType<typeof createLiveEditorSession>
  statuses: { status: string; info: string | undefined }[]
}> {
  const session = createLiveEditorSession({
    backendUrl: 'http://localhost:8080',
    flavorId: 'oxsts',
    languageId: 'oxsts',
    fileName: 'snippet.oxsts',
    initialCode: '',
    initialColorMode: 'dark',
  })
  const statuses: { status: string; info: string | undefined }[] = []
  session.onStatusChange((status, info) => {
    statuses.push({ status, info })
  })
  await session.start(document.createElement('div'))
  return { session, statuses }
}

function closeCurrentSocket(code: number, reason: string): void {
  const callbacks = connections.callbacks.at(-1)!
  callbacks.onClose({ code, reason })
  callbacks.onStopped()
}

describe('liveEditorSession', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    connections.callbacks.length = 0
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('stops on a session terminated close, shows the reason and ignores a reconnect', async () => {
    const { session, statuses } = await startSession()
    expect(statuses.at(-1)?.status).toBe('connected')

    closeCurrentSocket(CloseCode.SessionTerminated, 'Session terminated')
    await vi.advanceTimersByTimeAsync(10_000)
    session.reconnect()
    await vi.advanceTimersByTimeAsync(10_000)

    expect(statuses.at(-1)).toEqual({ status: 'terminated', info: 'Session terminated' })
    expect(connections.callbacks).toHaveLength(1)
    await session.dispose()
  })

  it('reconnects after a session limit close and shows the reason while it waits', async () => {
    const { session, statuses } = await startSession()

    closeCurrentSocket(CloseCode.TooManySessions, 'Session limit reached')

    expect(statuses.at(-1)).toEqual({ status: 'reconnecting', info: 'Session limit reached, reconnecting (attempt 1/3)...' })
    await vi.advanceTimersByTimeAsync(1_000)
    expect(connections.callbacks).toHaveLength(2)
    expect(statuses.at(-1)?.status).toBe('connected')
    await session.dispose()
  })

  it('reconnects after a lost connection as before', async () => {
    const { session, statuses } = await startSession()

    closeCurrentSocket(1006, '')

    expect(statuses.at(-1)).toEqual({ status: 'reconnecting', info: 'Reconnecting (attempt 1/3)...' })
    await vi.advanceTimersByTimeAsync(1_000)
    expect(connections.callbacks).toHaveLength(2)
    await session.dispose()
  })

  it('ignores a close of a socket the session has already replaced', async () => {
    const { session, statuses } = await startSession()
    const replaced = connections.callbacks[0]!

    closeCurrentSocket(1006, '')
    await vi.advanceTimersByTimeAsync(1_000)
    replaced.onClose({ code: CloseCode.SessionTerminated, reason: 'Session terminated' })
    closeCurrentSocket(1006, '')

    expect(statuses.at(-1)?.status).toBe('reconnecting')
    await session.dispose()
  })
})
