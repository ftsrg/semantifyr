/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { CompiledOxstsStatus, type CompiledOxstsResult } from '@semantifyr/editor-common'

import type { LiveFlavor } from '../../examples'
import type { ModelDocuments } from '../session/modelDocuments'
import { createDemandStore, type Store } from '../stores/store'
import { librariesOf } from './libraries'
import { initializationStepsOf } from './presentation'
import { createServedRuntime, type ServedRuntime } from './servedRuntime'
import type { DerivedDocument, LanguageUnit } from './types'

const COMPILE_DELAY_MS = 600

interface CompiledDocument {
  store: Store<DerivedDocument | null>
  attach: (runtime: ServedRuntime) => void
  dispose: () => void
}

function compiledOxsts(documentUri: string, text: string | null, message: string | null): DerivedDocument {
  return { id: 'oxsts', label: 'OXSTS', uri: documentUri.replace(/\.[^./]*$/, '.oxsts'), languageId: 'oxsts', text, message }
}

// The OXSTS the server compiles the model to, asked for after each edit while anyone shows it
function compiledDocument(command: string, documentUri: string, modelDocuments: ModelDocuments): CompiledDocument {
  let runtime: ServedRuntime | null = null
  let timer: ReturnType<typeof setTimeout> | null = null
  let generation = 0
  let stopFollowing = (): void => {}

  const compile = async (): Promise<void> => {
    const client = runtime?.servedClient()
    if (!client) {
      return
    }
    const current = ++generation
    let result: CompiledOxstsResult | null
    try {
      result = (await client.sendRequest('workspace/executeCommand', { command, arguments: [documentUri] })) as CompiledOxstsResult | null
    } catch (error) {
      result = { status: CompiledOxstsStatus.Errored, oxsts: null, message: error instanceof Error ? error.message : String(error) }
    }
    if (current === generation) {
      store.set(compiledOxsts(documentUri, result?.oxsts ?? null, result?.status === CompiledOxstsStatus.Compiled ? null : result?.message ?? null))
    }
  }

  const schedule = (): void => {
    if (timer) {
      clearTimeout(timer)
    }
    timer = setTimeout(() => {
      timer = null
      void compile()
    }, COMPILE_DELAY_MS)
  }

  const store = createDemandStore<DerivedDocument | null>(null, (demanded) => {
    stopFollowing()
    stopFollowing = () => {}
    if (!demanded) {
      return
    }
    const stopOpen = modelDocuments.onDidOpen(documentUri, schedule)
    const stopChange = modelDocuments.onDidChange(documentUri, schedule)
    const stopConnection = runtime?.connection.subscribe(() => {
      if (runtime?.connection.get().status === 'connected') {
        schedule()
      }
    })
    stopFollowing = () => {
      stopOpen()
      stopChange()
      stopConnection?.()
    }
    void compile()
  })

  return {
    store,
    attach: (attached) => {
      runtime = attached
    },
    dispose: () => {
      stopFollowing()
      if (timer) {
        clearTimeout(timer)
      }
    },
  }
}

// A language whose server is the backend: everything it needs is in the flavor records
export function createServedLanguage(id: string, flavors: readonly LiveFlavor[]): LanguageUnit {
  return {
    id,
    flavors,
    start: (flavor, context) => {
      const compiled = flavor.compiledOxstsCommand
        ? compiledDocument(flavor.compiledOxstsCommand, context.documentUri, context.modelDocuments)
        : null
      const runtime = createServedRuntime({
        backendUrl: context.backendUrl,
        documentUri: context.documentUri,
        sessionKind: context.sessionKind,
        flavor,
        socketFlavorId: flavor.id,
        clientLanguageId: flavor.languageId,
        colorMode: context.colorMode,
        steps: initializationStepsOf(id, context.backendUrl),
        libraryDocuments: librariesOf(flavor.id),
        ...(compiled ? { modelOxsts: compiled.store } : {}),
        onDispose: () => {
          compiled?.dispose()
        },
      })
      compiled?.attach(runtime)
      return runtime
    },
  }
}
