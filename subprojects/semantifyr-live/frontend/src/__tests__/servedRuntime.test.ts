/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { LanguageClientCallbacks } from '../lib/session/monaco'
import { CloseCode } from '../lib/session/reconnectController'
import type { ConnectionState } from '../lib/stores/connection'
import { InitializationSteps } from '../lib/stores/initialization'
import { liveFlavors } from '../examples'
import { servedFlavors } from './fixtures/flavors'

const connections = vi.hoisted(() => {
  return {
    callbacks: [] as LanguageClientCallbacks[],
    failures: 0,
    diagnostics: [] as (() => void)[],
  }
})

vi.mock('../lib/session/monaco', () => {
  return {
    initializeMonaco: async () => {},
    onDiagnosticsOf: (_uri: string, listener: () => void) => {
      connections.diagnostics.push(listener)
      return () => {}
    },
    connectLanguageClient: async (
      _backendUrl: string,
      _flavorId: string,
      _languageId: string,
      callbacks: LanguageClientCallbacks,
    ) => {
      connections.callbacks.push(callbacks)
      if (connections.failures > 0) {
        connections.failures--
        throw new Error('connection refused')
      }
      callbacks.onOpen?.()
      return {
        getLanguageClient: () => undefined,
        dispose: async () => {},
      }
    },
  }
})

const { createServedRuntime } = await import('../lib/language/servedRuntime')

type Status = Pick<ConnectionState, 'status' | 'reason'>

async function startRuntime(): Promise<{
  runtime: ReturnType<typeof createServedRuntime>
  statuses: Status[]
}> {
  const [flavor] = liveFlavors(servedFlavors)
  const runtime = createServedRuntime({
    backendUrl: 'http://localhost:8080',
    documentUri: 'file:///workspace/snippet.oxsts',
    flavor,
    socketFlavorId: flavor.id,
    clientLanguageId: flavor.languageId,
    colorMode: 'dark',
    steps: [
      InitializationSteps.connect,
      InitializationSteps.server,
      InitializationSteps.library,
      InitializationSteps.cases,
    ],
  })
  const statuses: Status[] = []
  runtime.connection.subscribe(() => {
    const { status, reason } = runtime.connection.get()
    statuses.push({ status, reason })
  })
  await vi.advanceTimersByTimeAsync(0)
  return { runtime, statuses }
}

function closeCurrentSocket(code: number, reason: string): void {
  const callbacks = connections.callbacks.at(-1)!
  callbacks.onClose({ code, reason })
  callbacks.onStopped()
}

function stepStates(runtime: ReturnType<typeof createServedRuntime>): string[] {
  return runtime.initialization.get().steps.map((step) => {
    return `${step.id}:${step.state}`
  })
}

describe('servedRuntime', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    connections.callbacks.length = 0
    connections.failures = 0
    connections.diagnostics.length = 0
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('stops on a session terminated close, shows the reason and ignores a reconnect', async () => {
    const { runtime, statuses } = await startRuntime()
    expect(statuses.at(-1)?.status).toBe('connected')

    closeCurrentSocket(CloseCode.SessionTerminated, 'Session terminated')
    await vi.advanceTimersByTimeAsync(10_000)
    runtime.reconnect()
    await vi.advanceTimersByTimeAsync(10_000)

    expect(statuses.at(-1)).toEqual({ status: 'failed', reason: 'Session terminated' })
    expect(runtime.connection.get().terminated).toBe(true)
    expect(connections.callbacks).toHaveLength(1)
    await runtime.dispose()
  })

  it('reconnects after a session limit close and shows the reason while it waits', async () => {
    const { runtime, statuses } = await startRuntime()

    closeCurrentSocket(CloseCode.TooManySessions, 'Session limit reached')

    expect(statuses.at(-1)).toEqual({ status: 'connecting', reason: 'Session limit reached, reconnecting (attempt 1/3)...' })
    await vi.advanceTimersByTimeAsync(1_000)
    expect(connections.callbacks).toHaveLength(2)
    expect(statuses.at(-1)?.status).toBe('connected')
    expect(runtime.connection.get().reconnectCount).toBe(1)
    await runtime.dispose()
  })

  it('reconnects after a lost connection as before', async () => {
    const { runtime, statuses } = await startRuntime()

    closeCurrentSocket(1006, '')

    expect(statuses.at(-1)).toEqual({ status: 'connecting', reason: 'Reconnecting (attempt 1/3)...' })
    await vi.advanceTimersByTimeAsync(1_000)
    expect(connections.callbacks).toHaveLength(2)
    await runtime.dispose()
  })

  it('ignores a close of a socket the runtime has already replaced', async () => {
    const { runtime, statuses } = await startRuntime()
    const replaced = connections.callbacks[0]!

    closeCurrentSocket(1006, '')
    await vi.advanceTimersByTimeAsync(1_000)
    replaced.onClose({ code: CloseCode.SessionTerminated, reason: 'Session terminated' })
    closeCurrentSocket(1006, '')

    expect(statuses.at(-1)?.status).toBe('connecting')
    await runtime.dispose()
  })

  it('reports its initialization: the connection and the server done, the library loading until the model\'s first diagnostics', async () => {
    const { runtime } = await startRuntime()

    expect(stepStates(runtime)).toEqual(['connect:done', 'server:done', 'library:running', 'cases:pending'])
    for (const listener of connections.diagnostics) {
      listener()
    }
    expect(stepStates(runtime)).toEqual(['connect:done', 'server:done', 'library:done', 'cases:running'])
    await runtime.dispose()
  })

  it('fails the connection step when the first connect fails, and completes it when a retry connects', async () => {
    connections.failures = 1
    const { runtime, statuses } = await startRuntime()
    expect(statuses.at(-1)).toEqual({ status: 'failed', reason: 'Failed to connect to LSP server' })
    expect(stepStates(runtime)).toEqual(['connect:failed', 'server:pending', 'library:pending', 'cases:pending'])

    runtime.reconnect()
    await vi.advanceTimersByTimeAsync(0)

    expect(statuses.at(-1)?.status).toBe('connected')
    expect(stepStates(runtime)).toEqual(['connect:done', 'server:done', 'library:running', 'cases:pending'])
    await runtime.dispose()
  })

  it('reports a disconnect on purpose apart from a failure', async () => {
    const { runtime, statuses } = await startRuntime()

    await runtime.disconnect()

    expect(statuses.at(-1)).toEqual({ status: 'disconnected', reason: null })
    await runtime.dispose()
  })
})
