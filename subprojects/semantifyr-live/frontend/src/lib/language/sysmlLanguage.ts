/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import * as vscode from 'vscode'
import type { LanguageClientWrapper } from 'monaco-languageclient/lcwrapper'

import type { LiveFlavor } from '../../examples'
import { connectWorkerLanguageClient } from '../session/monaco'
import { toVscodeDiagnostics } from '../session/problems'
import {
  createSysmlBridge,
  mapperProblems,
  SYSML_HIDDEN_LANGUAGE_ID,
  SYSML_OXSTS_FLAVOR_ID,
  SYSML_OXSTS_URI,
  type SysmlMapping,
} from '../session/sysmlBridge'
import { createStore } from '../stores/store'
import { libraryDocumentsStore, readSysmlLibrary, SYSML_LIBRARY_PREFIX } from './libraries'
import { initializationStepsOf } from './presentation'
import { createServedRuntime, type ServedRuntime } from './servedRuntime'
import type { DerivedDocument, LanguageUnit } from './types'

const MAPPING_DELAY_MS = 300

function mappedOxsts(text: string | null): readonly DerivedDocument[] {
  return [
    { id: 'oxsts', label: 'OXSTS', uri: SYSML_OXSTS_URI, languageId: 'oxsts', text },
  ]
}

// SysML's language server is the fork's worker in the page; the backend sees only the OXSTS the worker maps the model to
export function createSysmlLanguage(id: string, flavors: readonly LiveFlavor[]): LanguageUnit {
  // the worker holds the libraries, so it lives for the visit, across runtimes
  let worker: LanguageClientWrapper | null = null
  let workerStart: Promise<void> | null = null
  let onWorkerStopped = (): void => {}

  const ensureWorker = async (): Promise<void> => {
    if (worker?.isStarted()) {
      return
    }
    workerStart ??= (async () => {
      if (worker) {
        try {
          await worker.dispose()
        } catch {
          /* ignore */
        }
      }
      worker = await connectWorkerLanguageClient(
        id,
        new URL('/sysml-worker/syside-worker.cjs?library=library', window.location.href),
        () => {
          onWorkerStopped()
        },
      )
    })().finally(() => {
      workerStart = null
    })
    await workerStart
  }

  return {
    id,
    flavors,
    start: (flavor, context) => {
      const derivedDocuments = createStore(mappedOxsts(null))
      const modelOxsts = createStore<DerivedDocument | null>(null)
      let mapperDiagnostics: vscode.DiagnosticCollection | null = null
      let mappingTimer: ReturnType<typeof setTimeout> | null = null
      let mappingGeneration = 0
      let disposed = false

      const bridge = createSysmlBridge({
        fileUri: context.documentUri,
        getWorkerClient: () => {
          return worker?.getLanguageClient()
        },
        getOxstsClient: () => {
          return runtime.servedClient()
        },
      })

      const applyMapping = (mapping: SysmlMapping): void => {
        derivedDocuments.set(mappedOxsts(mapping.oxsts ?? null))
        modelOxsts.set(mappedOxsts(mapping.oxsts ?? null)[0] ?? null)
        mapperDiagnostics ??= vscode.languages.createDiagnosticCollection('semantifyr-mapper')
        mapperDiagnostics.set(vscode.Uri.parse(context.documentUri), toVscodeDiagnostics(mapperProblems(mapping)))
        if (mapping.oxsts !== undefined) {
          bridge.showOxsts(mapping.oxsts)
        }
      }

      const refreshMapping = async (): Promise<void> => {
        const generation = ++mappingGeneration
        let mapping: SysmlMapping
        try {
          mapping = await bridge.readMapping()
        } catch (error) {
          console.warn('semantifyr-live: SysML mapping failed', error)
          return
        }
        if (generation === mappingGeneration && !disposed) {
          applyMapping(mapping)
        }
      }

      // the worker starts once the session connects, and maps the model then
      const scheduleMappingRefresh = (): void => {
        if (!worker?.isStarted()) {
          return
        }
        if (mappingTimer) {
          clearTimeout(mappingTimer)
        }
        mappingTimer = setTimeout(() => {
          mappingTimer = null
          void refreshMapping()
        }, MAPPING_DELAY_MS)
      }

      const stopWatchingOpen = context.modelDocuments.onDidOpen(context.documentUri, scheduleMappingRefresh)
      const stopWatchingChanges = context.modelDocuments.onDidChange(context.documentUri, scheduleMappingRefresh)

      const runtime: ServedRuntime = createServedRuntime({
        backendUrl: context.backendUrl,
        documentUri: context.documentUri,
        sessionKind: context.sessionKind,
        flavor,
        socketFlavorId: SYSML_OXSTS_FLAVOR_ID,
        clientLanguageId: SYSML_HIDDEN_LANGUAGE_ID,
        colorMode: context.colorMode,
        steps: initializationStepsOf(id, context.backendUrl),
        derivedDocuments,
        modelOxsts,
        libraryDocuments: libraryDocumentsStore(flavor.id, context.backendUrl),
        // the OXSTS of the model is the document the bridge keeps open in the session
        readOnlyAddresses: {
          servesOxsts: true,
          modelOxstsUri: SYSML_OXSTS_URI,
        },
        readOtherLibrary: (uri) => {
          return uri.startsWith(SYSML_LIBRARY_PREFIX) ? readSysmlLibrary(uri, window.location.origin) : undefined
        },
        clientOf: () => {
          return bridge.client
        },
        translateNotification: bridge.translateNotification,
        afterConnect: async () => {
          await ensureWorker()
          scheduleMappingRefresh()
        },
        onDispose: () => {
          disposed = true
          if (mappingTimer) {
            clearTimeout(mappingTimer)
            mappingTimer = null
          }
          stopWatchingOpen()
          stopWatchingChanges()
          mapperDiagnostics?.dispose()
          mapperDiagnostics = null
          if (onWorkerStopped === handleWorkerStopped) {
            onWorkerStopped = () => {}
          }
        },
      })
      const handleWorkerStopped = (): void => {
        runtime.scheduleReconnect()
      }
      onWorkerStopped = handleWorkerStopped
      return { ...runtime, witnessUriOf: bridge.witnessUriOf }
    },
  }
}
