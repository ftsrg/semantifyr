/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Range } from '@semantifyr/editor-common'

import { findExample, liveFlavors, type LiveExample, type LiveFlavor } from '../../examples'
import { readAddress, resolveAddress, type InitialDocument } from './address'
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
import { createDocumentTabs, type DocumentTabs } from './documentTabs'
import { flavorOfFile, type DocumentFile, type OpenedFile } from './files'
import type { SharedResults } from './sharedResults'
import { createVerification, type RunTiming } from './verification'

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
  // the text when the document was opened or last saved
  savedText: string
  // the file the document was opened from or saved to
  file: DocumentFile | null
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

// The address named a language, a library or an example that does not exist, or carried what cannot be read; what was opened in its place
export interface AddressProblem {
  kind: 'language' | 'library' | 'example' | 'model' | 'results'
  requested: string
  flavorId: string
  exampleId: string
}

export type SessionKind =
  | 'full'
  | 'embed'
  | 'edit-only'

export interface LiveOptions {
  backendUrl: string
  // the flavor a bare address opens, the first served one when absent
  defaultFlavorId?: string
  sessionKind?: SessionKind
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
  readonly address: Store<AddressProblem | null>
  // the current document differs from its text when it was opened or last saved
  readonly unsaved: ReadableStore<boolean>
  // the sharer's verdicts of the link that opened the current document
  readonly sharedResults: ReadableStore<SharedResults | null>
  // the read-only documents beside the model in the editor; they belong to the running language
  readonly tabs: DocumentTabs
  readonly pendingVerifyAll: ReadableStore<boolean>
  readonly runTiming: ReadableStore<RunTiming | null>
  // the address of the page, or a path with its query and fragment
  start: (location: string) => void
  // the page moved to another address without loading, as a link of another fragment does
  openAddress: (location: string) => void
  dispose: () => Promise<void>
  selectModel: (flavorId: string, exampleId: string) => void
  openFile: (opened: OpenedFile) => void
  // back to the shared model or the file of the language, which the visit keeps
  showKept: (flavorId: string) => void
  markSaved: (file: DocumentFile | null, text: string) => void
  hasUnsaved: () => boolean
  currentText: () => string | null
  revealRange: (range: Range) => void
  reconnect: () => void
  // a new session for the open document, after the server ended the last one
  restartSession: () => void
  disconnect: () => Promise<void>
  refreshCases: () => void
  verifyAll: () => void
  verifyCase: (caseId: string) => void
  cancelVerification: () => void
  cancelCase: (caseId: string) => void
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
  const address = createStore<AddressProblem | null>(null)
  const unsaved = createStore(false)
  const sharedResults = createStore<SharedResults | null>(null)
  const tabs = createDocumentTabs()

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

  const textOf = (record: DocumentRecord): string => {
    return modelDocuments.read(record.uri) ?? record.text
  }

  const refreshUnsaved = (): void => {
    const record = currentDocument()
    unsaved.set(record !== undefined && textOf(record) !== record.savedText)
  }

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
          sharedResults.set(null)
        }
        refreshUnsaved()
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
    if (leaving?.key === key && document.replace !== true) {
      return
    }
    verification.cancel()
    if (leaving && leaving.key !== key) {
      putDocument({ ...leaving, text: textOf(leaving) })
    }
    const existing = document.replace === true ? undefined : documents.get().get(key)
    const record = existing ?? {
      key,
      flavorId: document.flavor.id,
      example: document.example,
      shared: document.shared,
      uri: documentUri(document.flavor),
      text: document.text,
      savedText: document.text,
      file: document.file ?? null,
    }
    putDocument(record)
    if (document.replace === true) {
      verification.forget(key)
    }
    verification.select(null)
    sharedResults.set(document.results ?? null)
    const { flavor, runtime } = workspace.get()
    if (runtime && flavor?.id === document.flavor.id) {
      workspace.set({ flavor, documentKey: key, runtime })
      modelDocuments.replace(record.uri, record.text)
      verification.scheduleDiscovery()
    } else {
      stopRuntime()
      tabs.closeAll()
      // the editor's model at the document's address outlives the runtime that showed another document there
      modelDocuments.replace(record.uri, record.text)
      startRuntime(document.flavor, record)
    }
    refreshUnsaved()
    if (document.results) {
      verification.holdSelection(document.results.selectedCase)
      verification.verifyAll()
    } else if (document.selectedCase !== undefined) {
      verification.select(document.selectedCase)
    }
  }

  const startRuntime = (flavor: LiveFlavor, record: DocumentRecord): void => {
    const unit = catalog.get().registry?.unitOf(flavor.id)
    if (!unit) {
      throw new Error(`no language serves the flavor ${flavor.id}`)
    }
    const started = unit.start(flavor, {
      backendUrl: options.backendUrl,
      documentUri: record.uri,
      colorMode: options.colorMode(),
      modelDocuments,
      ...(options.sessionKind ? { sessionKind: options.sessionKind } : {}),
    })
    workspace.set({ flavor, documentKey: record.key, runtime: started })
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

  const loadCatalog = async (location: string): Promise<void> => {
    try {
      const registry = createRegistry(liveFlavors(await server.fetchFlavors()))
      const initial = await resolveAddress(readAddress(location), registry, options.defaultFlavorId)
      if (disposed) {
        return
      }
      catalog.update((previous) => {
        return { ...previous, status: 'ready', registry }
      })
      address.set(initial.problem)
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
        void loadCatalog(location)
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
    address,
    tabs,
    pendingVerifyAll: verification.pending,
    runTiming: verification.timing,
    start: (location) => {
      if (typeof window !== 'undefined') {
        window.addEventListener('online', handleOnline)
      }
      void loadPortfolios()
      void loadCatalog(location)
    },
    openAddress: (location) => {
      const registry = catalog.get().registry
      if (!registry) {
        return
      }
      void resolveAddress(readAddress(location), registry, options.defaultFlavorId).then((initial) => {
        if (disposed) {
          return
        }
        address.set(initial.problem)
        open(initial)
      })
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
    unsaved,
    sharedResults,
    openFile: (opened) => {
      const registry = catalog.get().registry
      if (!registry) {
        return
      }
      const flavor = flavorOfFile(registry, opened.name, workspace.get().flavor)
      const example = flavor.examples[0]
      if (!example) {
        return
      }
      address.set(null)
      open({ flavor, example, text: opened.text, shared: true, problem: null, file: { name: opened.name, write: opened.write }, replace: true })
    },
    showKept: (flavorId) => {
      const flavor = catalog.get().registry?.flavorOf(flavorId)
      const record = documents.get().get(documentKey(flavorId, '', true))
      if (!flavor || !record) {
        return
      }
      address.set(null)
      open({ flavor, example: record.example, text: record.text, shared: true, problem: null })
    },
    markSaved: (file, text) => {
      const record = currentDocument()
      if (!record) {
        return
      }
      putDocument({ ...record, savedText: text, file: file ?? record.file })
      refreshUnsaved()
    },
    hasUnsaved: () => {
      return [...documents.get().values()].some((record) => {
        return record.key === workspace.get().documentKey ? textOf(record) !== record.savedText : record.text !== record.savedText
      })
    },
    selectModel: (flavorId, exampleId) => {
      const flavor = catalog.get().registry?.flavorOf(flavorId)
      const example = flavor ? findExample(flavor, exampleId) ?? flavor.examples[0] : undefined
      if (!flavor || !example) {
        return
      }
      address.set(null)
      open({ flavor, example, text: example.code, shared: false, problem: null })
    },
    currentText: () => {
      const record = currentDocument()
      return record ? textOf(record) : null
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
    restartSession: () => {
      const { flavor } = workspace.get()
      const record = currentDocument()
      if (!flavor || !record) {
        return
      }
      verification.cancel()
      stopRuntime()
      startRuntime(flavor, record)
    },
    disconnect: async () => {
      await workspace.get().runtime?.disconnect()
    },
    refreshCases: () => {
      void verification.discover()
    },
    verifyAll: verification.verifyAll,
    verifyCase: verification.verifyCase,
    cancelVerification: verification.cancel,
    cancelCase: verification.cancelCase,
    validateWitness: verification.validate,
    selectCase: verification.select,
    verificationPortfolioId,
  }
}
