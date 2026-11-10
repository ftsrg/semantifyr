/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { decodeCompressedBase64Url, parseLiveEditorUrl, type Range } from '@semantifyr/editor-common'

import { findExample, liveFlavors, type LiveExample, type LiveFlavor } from '../../examples'
import { createApi, type LiveServerApi } from '../api/rest'
import type { PortfolioInfo } from '../api/types'
import { createLanguageRegistry, type LanguageRegistry } from '../language/registry'
import type { LanguageRuntime } from '../language/types'
import type { ModelDocuments } from '../session/modelDocuments'
import type { ProblemEntry } from '../session/problems'
import { INITIAL_CONNECTION, type ConnectionState } from '../stores/connection'
import { initializationOf, type InitializationState } from '../stores/initialization'
import type { Settings } from '../stores/settings'
import { createStore, type ReadableStore, type Store } from '../stores/store'
import type { ResolvedColorMode } from '../util/colorMode'
import type { VerificationState } from '../verification/engine'
import { createVerification } from './verification'

const CATALOG_RETRY_MS = 5000
const DISCOVERY_DELAY_MS = 500

export type CatalogStatus =
  | 'loading'
  | 'waiting'
  | 'ready'

export interface Catalog {
  // waiting: the server does not answer, and the page retries
  status: CatalogStatus
  registry: LanguageRegistry | null
  portfolios: readonly PortfolioInfo[]
  defaultPortfolioId: string | null
}

export interface DocumentRecord {
  key: string
  flavorId: string
  example: LiveExample
  // opened from a link that names no example
  shared: boolean
  uri: string
  // the text when the document was last left; the editor's model holds it while the document is open
  text: string
}

export interface Workspace {
  flavor: LiveFlavor | null
  documentKey: string | null
  runtime: LanguageRuntime | null
}

export interface RevealRequest {
  uri: string
  range: Range
  sequence: number
}

export interface LiveOptions {
  backendUrl: string
  settings: Store<Settings>
  colorMode: () => ResolvedColorMode
  modelDocuments: ModelDocuments
  server?: Pick<LiveServerApi, 'fetchFlavors' | 'fetchPortfolios'>
  createRegistry?: (flavors: readonly [LiveFlavor, ...LiveFlavor[]]) => LanguageRegistry
  catalogRetryMs?: number
  discoveryDelayMs?: number
}

export interface Live {
  readonly settings: Store<Settings>
  readonly catalog: ReadableStore<Catalog>
  readonly workspace: ReadableStore<Workspace>
  readonly documents: ReadableStore<ReadonlyMap<string, DocumentRecord>>
  readonly connection: ReadableStore<ConnectionState>
  readonly initialization: ReadableStore<InitializationState>
  readonly verification: ReadableStore<ReadonlyMap<string, VerificationState>>
  readonly selection: ReadableStore<string | null>
  // filled by the editor from the model's diagnostics
  readonly problems: Store<readonly ProblemEntry[]>
  readonly reveal: ReadableStore<RevealRequest | null>
  start: (search: string) => void
  dispose: () => Promise<void>
  selectModel: (flavorId: string, exampleId: string) => void
  currentText: () => string | null
  revealRange: (range: Range) => void
  reconnect: () => void
  disconnect: () => Promise<void>
  refreshCases: () => void
  verifyAll: () => void
  verifyCase: (caseId: string) => void
  cancelVerification: () => void
  validateWitness: (caseId: string) => void
  selectCase: (caseId: string | null) => void
  verificationPortfolioId: () => string | undefined
}

export function documentKey(flavorId: string, exampleId: string, shared: boolean): string {
  return shared ? `${flavorId}#shared` : `${flavorId}#${exampleId}`
}

export function documentUri(flavor: LiveFlavor): string {
  return `file:///workspace/${flavor.fileName}`
}

interface InitialDocument {
  flavor: LiveFlavor
  example: LiveExample
  text: string
  shared: boolean
}

async function resolveInitialDocument(search: string, registry: LanguageRegistry): Promise<InitialDocument> {
  const parameters = parseLiveEditorUrl(search)
  const flavor = registry.flavorOf(parameters.flavor) ?? registry.flavors[0]
  const decodedCode = parameters.compressedCode ? await decodeCompressedBase64Url(parameters.compressedCode) : null
  const fallback = flavor.examples[0]
  if (!fallback) {
    throw new Error(`flavor ${flavor.id} has no examples`)
  }
  if (parameters.example) {
    const example = findExample(flavor, parameters.example) ?? fallback
    return { flavor, example, text: decodedCode ?? example.code, shared: false }
  }
  return { flavor, example: fallback, text: decodedCode ?? fallback.code, shared: decodedCode !== null }
}

export function createLive(options: LiveOptions): Live {
  const server = options.server ?? createApi(options.backendUrl)
  const createRegistry = options.createRegistry ?? createLanguageRegistry
  const { modelDocuments, settings } = options

  const catalog = createStore<Catalog>({ status: 'loading', registry: null, portfolios: [], defaultPortfolioId: null })
  const workspace = createStore<Workspace>({ flavor: null, documentKey: null, runtime: null })
  const documents = createStore<ReadonlyMap<string, DocumentRecord>>(new Map())
  const connection = createStore<ConnectionState>(INITIAL_CONNECTION)
  const initialization = createStore<InitializationState>(initializationOf([]))
  const problems = createStore<readonly ProblemEntry[]>([])
  const reveal = createStore<RevealRequest | null>(null)

  let disposed = false
  let catalogTimer: ReturnType<typeof setTimeout> | null = null
  let stopFollowingRuntime = (): void => {}

  const currentDocument = (): DocumentRecord | undefined => {
    const key = workspace.get().documentKey
    return key === null ? undefined : documents.get().get(key)
  }

  const verificationPortfolioId = (): string | undefined => {
    const { portfolios, defaultPortfolioId } = catalog.get()
    const chosen = settings.get().verificationPortfolioId
    const available = portfolios.some((portfolio) => {
      return portfolio.id === chosen && portfolio.available
    })
    return (available ? chosen : defaultPortfolioId) ?? undefined
  }

  const verification = createVerification({
    settings,
    modelDocuments,
    discoveryDelayMs: options.discoveryDelayMs ?? DISCOVERY_DELAY_MS,
    portfolioId: verificationPortfolioId,
    current: () => {
      const { flavor, runtime } = workspace.get()
      const record = currentDocument()
      if (!flavor || !runtime || !record) {
        return null
      }
      return { target: { key: record.key, uri: record.uri }, runtime, flavor }
    },
  })

  const putDocument = (record: DocumentRecord): void => {
    documents.update((previous) => {
      const changed = new Map(previous)
      changed.set(record.key, record)
      return changed
    })
  }

  const followRuntime = (runtime: LanguageRuntime, uri: string): (() => void) => {
    const copyConnection = (): void => {
      const previous = connection.get().status
      connection.set(runtime.connection.get())
      if (previous !== 'connected' && runtime.connection.get().status === 'connected') {
        void verification.discover()
      }
    }
    const copyInitialization = (): void => {
      initialization.set(runtime.initialization.get())
    }
    copyConnection()
    copyInitialization()
    const subscriptions = [
      runtime.connection.subscribe(copyConnection),
      runtime.initialization.subscribe(copyInitialization),
      modelDocuments.onDidOpen(uri, verification.scheduleDiscovery),
      modelDocuments.onDidChange(uri, (change) => {
        const key = workspace.get().documentKey
        if (!change.replaced && key !== null) {
          verification.documentEdited(key)
        }
        verification.scheduleDiscovery()
      }),
    ]
    return () => {
      for (const unsubscribe of subscriptions) {
        unsubscribe()
      }
    }
  }

  const stopRuntime = (): void => {
    stopFollowingRuntime()
    stopFollowingRuntime = () => {}
    void workspace.get().runtime?.dispose()
  }

  const open = (document: InitialDocument): void => {
    const key = documentKey(document.flavor.id, document.example.id, document.shared)
    const leaving = currentDocument()
    if (leaving?.key === key) {
      return
    }
    verification.cancel()
    if (leaving) {
      putDocument({ ...leaving, text: modelDocuments.read(leaving.uri) ?? leaving.text })
    }
    const record = documents.get().get(key) ?? {
      key,
      flavorId: document.flavor.id,
      example: document.example,
      shared: document.shared,
      uri: documentUri(document.flavor),
      text: document.text,
    }
    putDocument(record)
    verification.select(null)
    const { flavor, runtime } = workspace.get()
    if (runtime && flavor?.id === document.flavor.id) {
      workspace.set({ flavor, documentKey: key, runtime })
      modelDocuments.replace(record.uri, record.text)
      verification.scheduleDiscovery()
      return
    }
    stopRuntime()
    const unit = catalog.get().registry?.unitOf(document.flavor.id)
    if (!unit) {
      throw new Error(`no language serves the flavor ${document.flavor.id}`)
    }
    const started = unit.start(document.flavor, {
      backendUrl: options.backendUrl,
      documentUri: record.uri,
      colorMode: options.colorMode(),
      modelDocuments,
    })
    workspace.set({ flavor: document.flavor, documentKey: key, runtime: started })
    stopFollowingRuntime = followRuntime(started, record.uri)
  }

  const loadPortfolios = async (): Promise<void> => {
    try {
      const response = await server.fetchPortfolios()
      catalog.update((previous) => {
        return { ...previous, portfolios: response.portfolios, defaultPortfolioId: response.defaultPortfolio }
      })
    } catch {
      /* non-fatal: verification runs on the server's default */
    }
  }

  const loadCatalog = async (search: string): Promise<void> => {
    try {
      const registry = createRegistry(liveFlavors(await server.fetchFlavors()))
      const initial = await resolveInitialDocument(search, registry)
      if (disposed) {
        return
      }
      catalog.update((previous) => {
        return { ...previous, status: 'ready', registry }
      })
      open(initial)
    } catch {
      if (disposed) {
        return
      }
      catalog.update((previous) => {
        return { ...previous, status: 'waiting' }
      })
      catalogTimer = setTimeout(() => {
        catalogTimer = null
        void loadCatalog(search)
      }, options.catalogRetryMs ?? CATALOG_RETRY_MS)
    }
  }

  const reconnect = (): void => {
    workspace.get().runtime?.reconnect()
  }

  // a failure is retried when the browser is back online; a disconnect on purpose stays
  const handleOnline = (): void => {
    const { status, terminated } = connection.get()
    if (status === 'failed' && !terminated) {
      reconnect()
    }
  }

  return {
    settings,
    catalog,
    workspace,
    documents,
    connection,
    initialization,
    verification: verification.states,
    selection: verification.selection,
    problems,
    reveal,
    start: (search) => {
      if (typeof window !== 'undefined') {
        window.addEventListener('online', handleOnline)
      }
      void loadPortfolios()
      void loadCatalog(search)
    },
    dispose: async () => {
      disposed = true
      if (catalogTimer) {
        clearTimeout(catalogTimer)
        catalogTimer = null
      }
      if (typeof window !== 'undefined') {
        window.removeEventListener('online', handleOnline)
      }
      verification.dispose()
      stopFollowingRuntime()
      await workspace.get().runtime?.dispose()
    },
    selectModel: (flavorId, exampleId) => {
      const flavor = catalog.get().registry?.flavorOf(flavorId)
      const example = flavor ? findExample(flavor, exampleId) ?? flavor.examples[0] : undefined
      if (!flavor || !example) {
        return
      }
      open({ flavor, example, text: example.code, shared: false })
    },
    currentText: () => {
      const record = currentDocument()
      return record ? modelDocuments.read(record.uri) ?? record.text : null
    },
    revealRange: (range) => {
      const record = currentDocument()
      if (!record) {
        return
      }
      reveal.update((previous) => {
        return { uri: record.uri, range, sequence: (previous?.sequence ?? 0) + 1 }
      })
    },
    reconnect,
    disconnect: async () => {
      await workspace.get().runtime?.disconnect()
    },
    refreshCases: () => {
      void verification.discover()
    },
    verifyAll: verification.verifyAll,
    verifyCase: verification.verifyCase,
    cancelVerification: verification.cancel,
    validateWitness: verification.validate,
    selectCase: verification.select,
    verificationPortfolioId,
  }
}
