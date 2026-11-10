/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { LanguageClientWrapper } from 'monaco-languageclient/lcwrapper'

import type { LiveFlavor } from '../../examples'
import { SemantifyrLiveApi } from '../api/lspExtensions'
import { connectLanguageClient, initializeMonaco } from '../session/monaco'
import { wrapClientWithMetrics, type MetricsTracker } from '../session/lspMetrics'
import {
  createReconnectController,
  isTerminalClose,
  sessionLimitReason,
  type SocketClose,
} from '../session/reconnectController'
import {
  connectionConnected,
  connectionLost,
  INITIAL_CONNECTION,
  type ConnectionState,
} from '../stores/connection'
import {
  failStep,
  finishStep,
  initializationOf,
  InitializationSteps,
  startStep,
  type InitializationStepDefinition,
  type InitializationState,
} from '../stores/initialization'
import { constantStore, createStore, type ReadableStore } from '../stores/store'
import type { ResolvedColorMode } from '../util/colorMode'
import type { LspClient } from '../verification/engine'
import type { DerivedDocument, LanguageRuntime } from './types'

const MAX_RECONNECT_ATTEMPTS = 3
const RECONNECT_BACKOFF_MS = [
  1000,
  2000,
  4000,
]

const NO_DERIVED_DOCUMENTS = constantStore<readonly DerivedDocument[]>([])

export interface ServedRuntimeOptions {
  backendUrl: string
  flavor: LiveFlavor
  // the served flavor the WebSocket opens, and the language id whose documents its client syncs
  socketFlavorId: string
  clientLanguageId: string
  colorMode: ResolvedColorMode
  steps: readonly InitializationStepDefinition[]
  derivedDocuments?: ReadableStore<readonly DerivedDocument[]>
  clientOf?: (served: LspClient) => LspClient
  translateNotification?: (method: string, params: unknown) => unknown
  afterConnect?: () => Promise<void>
  onDispose?: () => void
}

export interface ServedRuntime extends LanguageRuntime {
  servedClient: () => LspClient | undefined
  scheduleReconnect: () => void
}

interface RawLanguageClient {
  onNotification: (
    method: string,
    listener: (params: unknown) => void,
  ) => { dispose: () => void } | undefined
}

function notifyListeners<T extends (...parameters: never[]) => void>(
  listeners: Iterable<T>,
  invoke: (listener: T) => void,
  label: string,
): void {
  for (const listener of listeners) {
    try {
      invoke(listener)
    } catch (error) {
      console.warn(`semantifyr-live: ${label} listener threw`, error)
    }
  }
}

async function tearDown(wrapper: LanguageClientWrapper): Promise<void> {
  try {
    await wrapper.dispose()
  } catch {
    /* ignore */
  }
}

export function createServedRuntime(options: ServedRuntimeOptions): ServedRuntime {
  const connection = createStore<ConnectionState>(INITIAL_CONNECTION)
  const initialization = createStore<InitializationState>(initializationOf(options.steps))

  let languageClient: LanguageClientWrapper | null = null
  let connectionGeneration = 0
  let lastClose: SocketClose | null = null
  let cancelled = false
  let disposed = false
  let metricsClient: (LspClient & MetricsTracker) | null = null
  let metricsServedClient: LspClient | null = null

  const progressListeners = new Set<(params: unknown) => void>()
  const notificationHandlers = new Map<string, Set<(params: unknown) => void>>()
  let attachedNotificationDisposables: { dispose: () => void }[] = []

  const changeStep = (change: (state: InitializationState, now: number) => InitializationState): void => {
    initialization.update((previous) => {
      return change(previous, Date.now())
    })
  }

  const reportConnected = (): void => {
    connection.update((previous) => {
      return connectionConnected(previous, Date.now())
    })
    changeStep((state, now) => {
      return startStep(finishStep(state, InitializationSteps.connect.id, now), InitializationSteps.cases.id, now)
    })
  }

  const reportFailed = (reason: string, terminated = false): void => {
    connection.update((previous) => {
      return connectionLost(previous, 'failed', reason, { terminated })
    })
    changeStep((state, now) => {
      return failStep(state, InitializationSteps.connect.id, now)
    })
  }

  const attachNotificationHandlers = (client: LanguageClientWrapper): void => {
    for (const disposable of attachedNotificationDisposables) {
      try {
        disposable.dispose()
      } catch {
        /* ignore */
      }
    }
    attachedNotificationDisposables = []
    const raw = client.getLanguageClient() as unknown as RawLanguageClient | undefined
    if (!raw) {
      return
    }
    for (const [method, listeners] of notificationHandlers) {
      try {
        const disposable = raw.onNotification(method, (params) => {
          const translated = options.translateNotification ? options.translateNotification(method, params) : params
          notifyListeners(listeners, (listener) => {
            listener(translated)
          }, `notification-${method}`)
        })
        if (disposable) {
          attachedNotificationDisposables.push(disposable)
        }
      } catch (error) {
        console.warn(`semantifyr-live: failed to attach notification listener for ${method}`, error)
      }
    }
  }

  const reconnectController = createReconnectController({
    maxAttempts: MAX_RECONNECT_ATTEMPTS,
    backoffMs: RECONNECT_BACKOFF_MS,
    connect: async () => {
      await doConnect()
    },
    isCancelled: () => {
      return cancelled
    },
    lastClose: () => {
      return lastClose
    },
    onStatus: (status, info) => {
      switch (status) {
        case 'connected':
          reportConnected()
          break
        case 'reconnecting':
          connection.update((previous) => {
            return connectionLost(previous, 'connecting', info ?? null, { reconnecting: true })
          })
          break
        case 'errored':
          reportFailed(info ?? 'Connection error')
          break
        case 'terminated':
          reportFailed(info ?? 'Session ended', true)
          break
      }
    },
  })

  const doConnect = async (): Promise<void> => {
    const generation = ++connectionGeneration
    lastClose = null
    if (languageClient) {
      await tearDown(languageClient)
      languageClient = null
    }
    metricsClient = null
    metricsServedClient = null
    const connected = await connectLanguageClient(
      options.backendUrl,
      options.socketFlavorId,
      options.clientLanguageId,
      {
        onProgress: (params: unknown) => {
          notifyListeners(progressListeners, (listener) => {
            listener(params)
          }, 'progress')
        },
        onStopped: () => {
          if (!cancelled) {
            reconnectController.schedule()
          }
        },
        onClose: (close) => {
          if (generation === connectionGeneration) {
            lastClose = close
          }
        },
      },
    )
    if (cancelled) {
      await tearDown(connected)
      return
    }
    languageClient = connected
    attachNotificationHandlers(connected)
    await options.afterConnect?.()
  }

  const start = async (): Promise<void> => {
    changeStep((state, now) => {
      return startStep(state, InitializationSteps.editor.id, now)
    })
    try {
      await initializeMonaco(options.colorMode)
    } catch {
      if (!cancelled) {
        reportFailed('Editor failed to start')
      }
      return
    }
    changeStep((state, now) => {
      return startStep(finishStep(state, InitializationSteps.editor.id, now), InitializationSteps.connect.id, now)
    })
    try {
      await doConnect()
    } catch {
      if (cancelled) {
        return
      }
      if (isTerminalClose(lastClose)) {
        reconnectController.schedule()
      } else {
        reportFailed(sessionLimitReason(lastClose) ?? 'Failed to connect to LSP server')
      }
      return
    }
    if (cancelled) {
      return
    }
    reconnectController.resetAttempts()
    reportConnected()
  }

  const servedClient = (): LspClient | undefined => {
    return languageClient?.getLanguageClient()
  }

  const client = (): LspClient | undefined => {
    const served = servedClient()
    if (!served) {
      metricsClient = null
      metricsServedClient = null
      return undefined
    }
    if (!metricsClient || metricsServedClient !== served) {
      metricsClient = wrapClientWithMetrics(options.clientOf ? options.clientOf(served) : served)
      metricsServedClient = served
    }
    return metricsClient
  }

  const addNotificationListener = (method: string, listener: (params: unknown) => void): (() => void) => {
    let listeners = notificationHandlers.get(method)
    if (!listeners) {
      listeners = new Set()
      notificationHandlers.set(method, listeners)
      if (languageClient) {
        attachNotificationHandlers(languageClient)
      }
    }
    listeners.add(listener)
    return () => {
      const registered = notificationHandlers.get(method)
      if (!registered) {
        return
      }
      registered.delete(listener)
      if (registered.size === 0) {
        notificationHandlers.delete(method)
        if (languageClient) {
          attachNotificationHandlers(languageClient)
        }
      }
    }
  }

  const reconnect = (): void => {
    if (disposed || reconnectController.terminated()) {
      return
    }
    cancelled = false
    reconnectController.cancel()
    reconnectController.resetAttempts()
    connection.update((previous) => {
      return connectionLost(previous, 'connecting', 'Reconnecting...', { reconnecting: true })
    })
    doConnect()
      .then(() => {
        if (!cancelled) {
          reportConnected()
        }
      })
      .catch(() => {
        reconnectController.schedule()
      })
  }

  const disconnect = async (): Promise<void> => {
    if (!languageClient) {
      return
    }
    cancelled = true
    reconnectController.cancel()
    const wrapper = languageClient
    languageClient = null
    metricsClient = null
    metricsServedClient = null
    connection.update((previous) => {
      return connectionLost(previous, 'disconnected', null)
    })
    await tearDown(wrapper)
  }

  const dispose = async (): Promise<void> => {
    if (disposed) {
      return
    }
    disposed = true
    cancelled = true
    reconnectController.cancel()
    if (languageClient) {
      await tearDown(languageClient)
    }
    languageClient = null
    metricsClient = null
    metricsServedClient = null
    options.onDispose?.()
  }

  void start()

  return {
    flavor: options.flavor,
    connection,
    initialization,
    derivedDocuments: options.derivedDocuments ?? NO_DERIVED_DOCUMENTS,
    api: new SemantifyrLiveApi(client, addNotificationListener),
    client,
    servedClient,
    metrics: () => {
      return metricsClient?.getMetrics() ?? null
    },
    addProgressListener: (listener) => {
      progressListeners.add(listener)
      return () => {
        progressListeners.delete(listener)
      }
    },
    scheduleReconnect: () => {
      if (!cancelled) {
        reconnectController.schedule()
      }
    },
    reconnect,
    disconnect,
    dispose,
  }
}
