/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { LanguageClientWrapper } from 'monaco-languageclient/lcwrapper'

import type { LiveFlavor } from '../../examples'
import { SemantifyrLiveApi } from '../api/lspExtensions'
import { connectLanguageClient, initializeMonaco, onDiagnosticsOf, type ReadOnlyAddresses } from '../session/monaco'
import { wrapClientWithMetrics, type MetricsTracker } from '../session/lspMetrics'
import { createReconnectController, SessionStartTimeout, type SocketClose } from '../session/reconnectController'
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
import type { DerivedDocument, LanguageRuntime, LibraryDocument } from './types'

const RECONNECT_BACKOFF_MS = [
  1000,
  2000,
  4000,
  8000,
  16000,
  30000,
]

// a server that takes the socket and never answers the start of the session is given up on and asked again
const SESSION_START_TIMEOUT_MS = 30000

const NO_DERIVED_DOCUMENTS = constantStore<readonly DerivedDocument[]>([])
const NO_MODEL_OXSTS = constantStore<DerivedDocument | null>(null)
const NO_LIBRARY_DOCUMENTS = constantStore<readonly LibraryDocument[]>([])

export interface ServedRuntimeOptions {
  backendUrl: string
  // the model's document, whose first diagnostics end the initialization of the language server
  documentUri: string
  sessionKind?: string | undefined
  flavor: LiveFlavor
  // the served flavor the WebSocket opens, and the language id whose documents its client syncs
  socketFlavorId: string
  clientLanguageId: string
  colorMode: ResolvedColorMode
  steps: readonly InitializationStepDefinition[]
  derivedDocuments?: ReadableStore<readonly DerivedDocument[]>
  modelOxsts?: ReadableStore<DerivedDocument | null>
  libraryDocuments?: ReadableStore<readonly LibraryDocument[]>
  // reads the library files the session does not serve, the rest through the session
  readOtherLibrary?: (uri: string) => Promise<string | null> | undefined
  readOnlyAddresses?: ReadOnlyAddresses
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
      const started = finishStep(finishStep(state, InitializationSteps.connect.id, now), InitializationSteps.server.id, now)
      return startStep(startStep(started, InitializationSteps.initialize.id, now), InitializationSteps.cases.id, now)
    })
  }

  const reportOpen = (): void => {
    changeStep((state, now) => {
      return startStep(finishStep(state, InitializationSteps.connect.id, now), InitializationSteps.server.id, now)
    })
  }

  // a clean model gets an empty set; late diagnostics end an initialization that timed out, never one a lost session failed
  const stopWatchingDiagnostics = onDiagnosticsOf(options.documentUri, () => {
    const step = initialization.get().steps.find((candidate) => {
      return candidate.id === InitializationSteps.initialize.id
    })
    const timedOut = step?.state === 'failed' && step.reason !== undefined
    if (step?.state !== 'running' && !timedOut) {
      return
    }
    changeStep((state, now) => {
      return finishStep(state, InitializationSteps.initialize.id, now)
    })
  })

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

  // a session refused or lost before initialization ends fails the steps it was at, until an attempt connects
  const failRunningSteps = (): void => {
    changeStep((state, now) => {
      return state.steps.filter((step) => {
        return step.state === 'running'
      }).reduce((failed, step) => {
        return failStep(failed, step.id, now)
      }, state)
    })
  }

  const reconnectController = createReconnectController({
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
            return connectionLost(previous, 'connecting', info?.reason ?? null, { reconnecting: true, refused: info?.refused ?? false, retryAt: info?.retryAt ?? null })
          })
          failRunningSteps()
          break
        case 'terminated':
          reportFailed(info?.reason ?? 'Session ended', true)
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
    // the language client waits for its initialize answer even when the server closes the socket first, as it does on a refusal
    let closedEarly = (): void => {}
    const closedBeforeStart = new Promise<never>((_resolve, reject) => {
      closedEarly = () => {
        reject(new Error('The server closed the session before it started'))
      }
    })
    const starting = connectLanguageClient(
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
            closedEarly()
          }
        },
        onOpen: reportOpen,
      },
      options.sessionKind,
      options.flavor.languageId,
      options.readOnlyAddresses,
    )
    let timeOut = (): void => {}
    const startTimedOut = new Promise<never>((_resolve, reject) => {
      timeOut = () => {
        reject(new SessionStartTimeout(`The server did not start the session within ${SESSION_START_TIMEOUT_MS / 1000} s.`))
      }
    })
    const startTimer = setTimeout(timeOut, SESSION_START_TIMEOUT_MS)
    let connected: LanguageClientWrapper
    try {
      connected = await Promise.race([starting, closedBeforeStart, startTimedOut])
    } catch (error) {
      void starting.then(tearDown, () => {})
      throw error
    } finally {
      clearTimeout(startTimer)
    }
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
      return startStep(state, InitializationSteps.connect.id, now)
    })
    try {
      await initializeMonaco(options.colorMode)
    } catch {
      if (!cancelled) {
        reportFailed('Editor failed to start')
      }
      return
    }
    try {
      await doConnect()
    } catch (error) {
      if (!cancelled) {
        reconnectController.schedule(error)
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
      return connectionLost(previous, 'connecting', previous.reason, { reconnecting: true, refused: previous.refused })
    })
    doConnect()
      .then(() => {
        if (!cancelled) {
          reportConnected()
        }
      })
      .catch((error: unknown) => {
        reconnectController.schedule(error)
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
    stopWatchingDiagnostics()
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

  const api = new SemantifyrLiveApi(client, addNotificationListener)

  return {
    flavor: options.flavor,
    connection,
    initialization,
    derivedDocuments: options.derivedDocuments ?? NO_DERIVED_DOCUMENTS,
    modelOxsts: options.modelOxsts ?? NO_MODEL_OXSTS,
    libraryDocuments: options.libraryDocuments ?? NO_LIBRARY_DOCUMENTS,
    api,
    readLibrary: (uri) => {
      return options.readOtherLibrary?.(uri) ?? api.readDocument(uri)
    },
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
