/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { FlavorInfo } from '@semantifyr/editor-common'
import { liveFlavors, type LiveFlavor } from '../../examples'
import { SemantifyrLiveApi, SemantifyrLiveMethods } from '../../lib/api/lspExtensions'
import type { ActiveVerificationInfo } from '../../lib/api/types'
import type { LanguageRegistry } from '../../lib/language/registry'
import type { LanguageRuntime, LanguageUnit } from '../../lib/language/types'
import type { ModelChange, ModelDocuments } from '../../lib/session/modelDocuments'
import { connectionConnected, connectionLost, INITIAL_CONNECTION, type ConnectionState } from '../../lib/stores/connection'
import { finishStep, initializationOf, InitializationSteps, startStep } from '../../lib/stores/initialization'
import { createSettingsStore } from '../../lib/stores/settings'
import { constantStore, createStore } from '../../lib/stores/store'
import type { LspClient } from '../../lib/verification/engine'
import { createLive, type Live, type LiveOptions } from '../../lib/workspace/live'
import { servedFlavors } from '../fixtures/flavors'

export interface FakeModelDocuments extends ModelDocuments {
  open: (uri: string, text: string) => void
  edit: (uri: string, text: string) => void
}

export function fakeModelDocuments(): FakeModelDocuments {
  const texts = new Map<string, string>()
  const openListeners = new Set<{ uri: string, listener: () => void }>()
  const changeListeners = new Set<{ uri: string, listener: (change: ModelChange) => void }>()

  const notifyChange = (uri: string, change: ModelChange): void => {
    for (const entry of [...changeListeners]) {
      if (entry.uri === uri) {
        entry.listener(change)
      }
    }
  }

  return {
    isOpen: (uri) => {
      return texts.has(uri)
    },
    read: (uri) => {
      return texts.get(uri) ?? null
    },
    replace: (uri, text) => {
      if (!texts.has(uri) || texts.get(uri) === text) {
        return
      }
      texts.set(uri, text)
      notifyChange(uri, { replaced: true })
    },
    onDidOpen: (uri, listener) => {
      const entry = { uri, listener }
      openListeners.add(entry)
      return () => {
        openListeners.delete(entry)
      }
    },
    onDidChange: (uri, listener) => {
      const entry = { uri, listener }
      changeListeners.add(entry)
      return () => {
        changeListeners.delete(entry)
      }
    },
    open: (uri, text) => {
      texts.set(uri, text)
      for (const entry of [...openListeners]) {
        if (entry.uri === uri) {
          entry.listener()
        }
      }
    },
    edit: (uri, text) => {
      texts.set(uri, text)
      notifyChange(uri, { replaced: false })
    },
  }
}

export interface FakeRuntime extends LanguageRuntime {
  connect: () => void
  // the model's first diagnostics arrived
  initialized: () => void
  // the server ended the session
  terminate: () => void
  progress: (params: unknown) => void
  // the server's notification of the session's verifications
  report: (active: ActiveVerificationInfo[]) => void
}

// a runtime whose language server initializes at the connect, or only when told to when it is `initializing`
export function fakeRuntime(flavor: LiveFlavor, client: LspClient, initializing = false): FakeRuntime {
  const connection = createStore<ConnectionState>(INITIAL_CONNECTION)
  const initialization = createStore(initializationOf([
    InitializationSteps.connect,
    InitializationSteps.initialize,
    InitializationSteps.cases,
  ]))
  const initialized = (): void => {
    initialization.update((state) => {
      return finishStep(state, InitializationSteps.initialize.id, 0)
    })
  }
  const progressListeners = new Set<(params: unknown) => void>()
  const notificationListeners = new Set<{ method: string, listener: (params: unknown) => void }>()
  let connected = false
  return {
    flavor,
    connection,
    initialization,
    derivedDocuments: constantStore([]),
    modelOxsts: constantStore(null),
    libraryDocuments: constantStore([]),
    readLibrary: async () => {
      return null
    },
    api: new SemantifyrLiveApi(() => {
      return connected ? client : undefined
    }, (method, listener) => {
      const entry = { method, listener }
      notificationListeners.add(entry)
      return () => {
        notificationListeners.delete(entry)
      }
    }),
    client: () => {
      return connected ? client : undefined
    },
    metrics: () => {
      return null
    },
    addProgressListener: (listener) => {
      progressListeners.add(listener)
      return () => {
        progressListeners.delete(listener)
      }
    },
    reconnect: () => {},
    disconnect: async () => {},
    dispose: async () => {},
    connect: () => {
      connected = true
      initialization.update((state) => {
        const started = startStep(finishStep(state, InitializationSteps.connect.id, 0), InitializationSteps.initialize.id, 0)
        return startStep(started, InitializationSteps.cases.id, 0)
      })
      connection.update((previous) => {
        return connectionConnected(previous, 0)
      })
      if (!initializing) {
        initialized()
      }
    },
    initialized,
    terminate: () => {
      connected = false
      connection.update((previous) => {
        return connectionLost(previous, 'failed', 'Session terminated by an administrator', { terminated: true })
      })
    },
    progress: (params) => {
      for (const listener of progressListeners) {
        listener(params)
      }
    },
    report: (active) => {
      for (const entry of notificationListeners) {
        if (entry.method === SemantifyrLiveMethods.VERIFICATIONS_CHANGED) {
          entry.listener({ active })
        }
      }
    },
  }
}

export function fakeRegistry(
  flavors: readonly [LiveFlavor, ...LiveFlavor[]],
  start: (flavor: LiveFlavor) => LanguageRuntime,
): LanguageRegistry {
  const units: LanguageUnit[] = flavors.map((flavor) => {
    return {
      id: flavor.languageId,
      flavors: [flavor],
      start,
    }
  })
  return {
    units,
    flavors,
    unitOf: (flavorId) => {
      return units.find((unit) => {
        return unit.flavors[0]?.id === flavorId
      })
    },
    flavorOf: (flavorId) => {
      return flavors.find((flavor) => {
        return flavor.id === flavorId
      })
    },
  }
}

export interface TestLive {
  live: Live
  models: FakeModelDocuments
  runtimes: FakeRuntime[]
  runtime: () => FakeRuntime
}

// A Live over fake runtimes that answer through the given client; the editor's models are plain text
export function createTestLive(client: LspClient, served: readonly FlavorInfo[] = servedFlavors, { initializing = false, ...options }: Pick<LiveOptions, 'defaultFlavorId' | 'server' | 'catalogRetryMs' | 'discoveryRetryMs' | 'discoveryTimeoutMs' | 'initializationTimeoutMs'> & { initializing?: boolean } = {}): TestLive {
  const models = fakeModelDocuments()
  const runtimes: FakeRuntime[] = []
  const live = createLive({
    backendUrl: 'https://test.example',
    settings: createSettingsStore(),
    colorMode: () => {
      return 'dark'
    },
    modelDocuments: models,
    discoveryDelayMs: 0,
    server: {
      fetchFlavors: async () => {
        return [...served]
      },
      fetchPortfolios: async () => {
        return {
          portfolios: [
            { id: 'smart', displayName: 'Smart full', description: '', available: true },
            { id: 'theta', displayName: 'Theta', description: '', available: true },
          ],
          defaultPortfolio: 'smart',
        }
      },
    },
    ...options,
    createRegistry: (flavors) => {
      return fakeRegistry(flavors, (flavor) => {
        const runtime = fakeRuntime(flavor, client, initializing)
        runtimes.push(runtime)
        return runtime
      })
    },
  })
  return {
    live,
    models,
    runtimes,
    runtime: () => {
      const runtime = runtimes.at(-1)
      if (!runtime) {
        throw new Error('no runtime started')
      }
      return runtime
    },
  }
}

export function testFlavors(): readonly [LiveFlavor, ...LiveFlavor[]] {
  return liveFlavors(servedFlavors)
}
