/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { LiveFlavor } from '../../examples'
import type { SemantifyrLiveApi } from '../api/lspExtensions'
import type { LspMetrics } from '../session/lspMetrics'
import type { ModelDocuments } from '../session/modelDocuments'
import type { ConnectionState } from '../stores/connection'
import type { InitializationState } from '../stores/initialization'
import type { ReadableStore, Store } from '../stores/store'
import type { ResolvedColorMode } from '../util/colorMode'
import type { LspClient } from '../verification/engine'

export interface RuntimeContext {
  backendUrl: string
  // the model's document; a runtime never follows what the editor shows
  documentUri: string
  colorMode: ResolvedColorMode
  modelDocuments: ModelDocuments
  // the session's kind the backend counts, its default when absent
  sessionKind?: string
}

export interface DerivedDocument {
  id: string
  label: string
  uri: string
  languageId: string
  // null when the model gives none, with the reason in the message or the problems
  text: string | null
  message?: string | null
}

export interface LibraryDocument {
  // library or semantic-library
  kind: LibraryKind
  // the file URI the language reads it by
  uri: string
  languageId: string
}

export type LibraryKind =
  | 'library'
  | 'semantic-library'

// Outlives the model's document: switching the example keeps the runtime and what it has loaded
export interface LanguageRuntime {
  readonly flavor: LiveFlavor
  readonly connection: ReadableStore<ConnectionState>
  readonly initialization: Store<InitializationState>
  readonly derivedDocuments: ReadableStore<readonly DerivedDocument[]>
  // the OXSTS the model compiles to, null for a language that is OXSTS; it may compute only while subscribed
  readonly modelOxsts: ReadableStore<DerivedDocument | null>
  readonly libraryDocuments: readonly LibraryDocument[]
  readonly api: SemantifyrLiveApi
  // reads a library file by its file URI
  readLibrary: (uri: string) => Promise<string | null>
  // the OXSTS witness of a trace whose witness URI names the language's own trace
  witnessUriOf?: (witnessUri: string) => string | undefined
  client: () => LspClient | undefined
  metrics: () => LspMetrics | null
  addProgressListener: (listener: (params: unknown) => void) => () => void
  reconnect: () => void
  disconnect: () => Promise<void>
  dispose: () => Promise<void>
}

export interface LanguageUnit {
  // the editor language of the model
  id: string
  flavors: readonly LiveFlavor[]
  start: (flavor: LiveFlavor, context: RuntimeContext) => LanguageRuntime
}
