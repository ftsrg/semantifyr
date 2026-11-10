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
}

export interface DerivedDocument {
  id: string
  label: string
  uri: string
  languageId: string
  // null when the model gives none, with the reason in the problems
  text: string | null
}

// Outlives the model's document: switching the example keeps the runtime and what it has loaded
export interface LanguageRuntime {
  readonly flavor: LiveFlavor
  readonly connection: ReadableStore<ConnectionState>
  readonly initialization: Store<InitializationState>
  readonly derivedDocuments: ReadableStore<readonly DerivedDocument[]>
  readonly api: SemantifyrLiveApi
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
