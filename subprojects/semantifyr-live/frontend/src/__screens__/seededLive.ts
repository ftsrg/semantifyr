/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { PortfolioInfo, VerificationCaseSpecification, VerificationTrace } from '@semantifyr/editor-common'

import { findExample, liveFlavors, type LiveFlavor } from '../examples'
import { SemantifyrLiveApi } from '../lib/api/lspExtensions'
import { librariesOf } from '../lib/language/libraries'
import { initializationStepsOf } from '../lib/language/presentation'
import { createLanguageRegistry } from '../lib/language/registry'
import type { DerivedDocument, LanguageRuntime } from '../lib/language/types'
import type { ProblemEntry } from '../lib/session/problems'
import { connectionConnected, INITIAL_CONNECTION, type ConnectionState } from '../lib/stores/connection'
import { finishThrough, initializationOf, startStep, type InitializationState } from '../lib/stores/initialization'
import { createSettingsStore, type Settings } from '../lib/stores/settings'
import { constantStore, createStore } from '../lib/stores/store'
import type { VerificationCaseState, VerificationState } from '../lib/verification/engine'
import { createDocumentTabs, type ReadOnlyDocument } from '../lib/workspace/documentTabs'
import { documentKey, documentUri, type AddressProblem, type Catalog, type DocumentRecord, type Live } from '../lib/workspace/live'
import type { SharedResults } from '../lib/workspace/sharedResults'
import type { RunTiming } from '../lib/workspace/verification'
import { createShellState, type ShellState } from '../shells/shellState'
import { servedFlavors } from '../__tests__/fixtures/flavors'

export interface Seed {
  flavorId?: string
  exampleId?: string
  catalog?: Catalog['status']
  connection?: ConnectionState
  // the step running, every step before it done; null when initialization is over
  initializingAt?: string | null
  pending?: boolean
  verification?: VerificationState
  selection?: string | null
  address?: AddressProblem | null
  tab?: ReadOnlyDocument
  // the OXSTS the runtime gives for the model, which the OXSTS tab shows
  modelOxsts?: string
  settings?: Partial<Settings>
  timing?: RunTiming | null
  verifyBlocked?: boolean
  unsaved?: boolean
  sharedResults?: SharedResults | null
  // the model's text in place of the example's
  text?: string
  // the name of the file the model was opened from
  fileName?: string
  portfolios?: PortfolioInfo[]
}

export interface Seeded {
  live: Live
  shell: ShellState
}

const BACKEND = 'http://live.example'

export const CONNECTED = connectionConnected(INITIAL_CONNECTION, Date.now())

function initialization(languageId: string, runningStep: string | null): InitializationState {
  const steps = initializationOf(initializationStepsOf(languageId, BACKEND))
  const now = Date.now()
  if (runningStep === null) {
    return finishThrough(steps, 'cases', now - 2000)
  }
  const index = steps.steps.findIndex((step) => {
    return step.id === runningStep
  })
  const before = steps.steps[index - 1]
  const finished = before ? finishThrough(steps, before.id, now - 6000) : steps
  return startStep(finished, runningStep, now - 6000)
}

function runtimeOf(flavor: LiveFlavor, connection: ConnectionState, steps: InitializationState, modelOxsts: string | undefined): LanguageRuntime {
  return {
    flavor,
    connection: constantStore(connection),
    initialization: createStore(steps),
    derivedDocuments: constantStore([]),
    modelOxsts: constantStore<DerivedDocument | null>(modelOxsts === undefined ? null : { id: 'oxsts', label: 'OXSTS', uri: 'file:///workspace/snippet.oxsts', languageId: 'oxsts', text: modelOxsts }),
    libraryDocuments: librariesOf(flavor.id),
    api: new SemantifyrLiveApi(() => {
      return undefined
    }, () => {
      return () => {}
    }),
    readLibrary: () => {
      return Promise.resolve(null)
    },
    client: () => {
      return undefined
    },
    metrics: () => {
      return null
    },
    addProgressListener: () => {
      return () => {}
    },
    reconnect: () => {},
    disconnect: () => {
      return Promise.resolve()
    },
    dispose: () => {
      return Promise.resolve()
    },
  }
}

// a Live over stores set to one state, with actions that do nothing
export function seedLive(seed: Seed): Seeded {
  const registry = createLanguageRegistry(liveFlavors(servedFlavors))
  const flavor = registry.flavorOf(seed.flavorId ?? 'sysml') ?? registry.flavors[0]
  const example = findExample(flavor, seed.exampleId) ?? flavor.examples[0]
  if (!example) {
    throw new Error(`flavor ${flavor.id} has no examples`)
  }
  const ready = (seed.catalog ?? 'ready') === 'ready'
  const connection = seed.connection ?? CONNECTED
  const steps = initialization(flavor.languageId, seed.initializingAt ?? null)
  const runtime = runtimeOf(flavor, connection, steps, seed.modelOxsts)
  const key = documentKey(flavor.id, example.id, false)
  const text = seed.text ?? example.code
  const record: DocumentRecord = { key, flavorId: flavor.id, example, shared: false, uri: documentUri(flavor), text, savedText: text, file: seed.fileName === undefined ? null : { name: seed.fileName, write: null } }
  const settings = createSettingsStore()
  settings.update((previous) => {
    return { ...previous, resultsColumnWidth: 0, caseListHeight: 0, resultsColumnCollapsed: false, ...seed.settings }
  })
  const tabs = createDocumentTabs()
  if (seed.tab) {
    tabs.open(seed.tab)
  }
  const verification = new Map<string, VerificationState>()
  if (seed.verification) {
    verification.set(key, seed.verification)
  }
  const shell = createShellState()
  shell.verifyBlocked.set(seed.verifyBlocked ?? false)
  const live: Live = {
    settings,
    catalog: constantStore<Catalog>({ status: seed.catalog ?? 'ready', registry: ready ? registry : null, portfolios: seed.portfolios ?? [], defaultPortfolioId: 'smart-full' }),
    workspace: constantStore(ready ? { flavor, documentKey: key, runtime } : { flavor: null, documentKey: null, runtime: null }),
    documents: constantStore(new Map(ready ? [[key, record]] : [])),
    connection: constantStore(connection),
    initialization: constantStore(ready ? steps : initializationOf([])),
    verification: constantStore(verification),
    selection: constantStore(seed.selection ?? null),
    problems: createStore<readonly ProblemEntry[]>([]),
    reveal: constantStore(null),
    address: createStore(seed.address ?? null),
    unsaved: constantStore(seed.unsaved ?? false),
    sharedResults: constantStore(seed.sharedResults ?? null),
    tabs,
    pendingVerifyAll: constantStore(seed.pending ?? false),
    runTiming: constantStore(seed.timing ?? null),
    start: () => {},
    openAddress: () => {},
    dispose: () => {
      return Promise.resolve()
    },
    selectModel: () => {},
    openFile: () => {},
    showKept: () => {},
    markSaved: () => {},
    hasUnsaved: () => {
      return seed.unsaved ?? false
    },
    currentText: () => {
      return text
    },
    revealRange: () => {},
    reconnect: () => {},
    restartSession: () => {},
    disconnect: () => {
      return Promise.resolve()
    },
    refreshCases: () => {},
    verifyAll: () => {},
    verifyCase: () => {},
    cancelVerification: () => {},
    cancelCase: () => {},
    validateWitness: () => {},
    selectCase: () => {},
    verificationPortfolioId: () => {
      return 'smart-full'
    },
  }
  return { live, shell }
}

export function caseOf(id: string, line: number, uri = 'file:///workspace/snippet.sysml'): VerificationCaseSpecification {
  return {
    id,
    label: id,
    location: { uri, range: { start: { line, character: 0 }, end: { line: line + 12, character: 1 } } },
  }
}

const EMPTY_TRACE: VerificationTrace = {
  callTrace: { initializedStep: { traces: [] }, steps: [] },
  witnessState: { initializedStep: { values: [] }, steps: [] },
  witnessUri: null,
}

export function finished(caseInfo: VerificationCaseSpecification, status: 'passed' | 'failed', seconds: number): VerificationCaseState {
  return {
    caseInfo,
    status,
    resultStatus: status,
    metrics: { totalDuration: `PT${seconds}S`, preparationDuration: 'PT0S', verificationDuration: `PT${seconds}S`, backAnnotationDuration: 'PT0S' },
    backendId: 'theta',
    portfolioId: 'smart-full',
    trace: EMPTY_TRACE,
    witnessValidation: 'valid',
  }
}
