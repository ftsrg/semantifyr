/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import * as vscode from 'vscode'

import type { ResolvedColorMode } from '../util/colorMode'
import type { Live } from '../workspace/live'
import { casesToMarkers } from './caseMarkers'
import { createEditor, type EditorInstance } from './monaco'
import { problemsOf, toVscodeDiagnostics } from './problems'

export interface EditorBinding {
  dispose: () => Promise<void>
}

export interface EditorDocument {
  uri: string
  languageId: string
  text: string
}

// One editor on the model's document: it feeds the problems store, draws the verdict markers and performs reveal requests
export function bindEditor(host: HTMLElement, live: Live, document: EditorDocument, colorMode: ResolvedColorMode): EditorBinding {
  const uri = vscode.Uri.parse(document.uri)
  let instance: EditorInstance | null = null
  let disposed = false
  const unsubscribes: (() => void)[] = []

  const attach = (editorInstance: EditorInstance): void => {
    instance = editorInstance

    const refreshProblems = (): void => {
      live.problems.set(problemsOf(uri))
    }
    const diagnosticsSubscription = vscode.languages.onDidChangeDiagnostics(refreshProblems)
    unsubscribes.push(() => {
      diagnosticsSubscription.dispose()
    })
    refreshProblems()

    const verifyDiagnostics = vscode.languages.createDiagnosticCollection('semantifyr-verify')
    unsubscribes.push(() => {
      verifyDiagnostics.dispose()
    })
    const refreshMarkers = (): void => {
      const key = live.workspace.get().documentKey
      const cases = key === null ? [] : live.verification.get().get(key)?.cases ?? []
      const current = cases.filter((caseState) => {
        return caseState.outdated !== true
      })
      verifyDiagnostics.set(uri, toVscodeDiagnostics(casesToMarkers(current)))
    }
    unsubscribes.push(live.verification.subscribe(refreshMarkers), live.workspace.subscribe(refreshMarkers))
    refreshMarkers()

    unsubscribes.push(live.reveal.subscribe(() => {
      const request = live.reveal.get()
      const editor = instance?.editorApp.getEditor()
      if (request?.uri !== document.uri || !editor) {
        return
      }
      const lineNumber = request.range.start.line + 1
      editor.revealLineInCenter(lineNumber)
      editor.setPosition({ lineNumber, column: request.range.start.character + 1 })
      editor.focus()
    }))
  }

  const disposeInstance = async (editorInstance: EditorInstance): Promise<void> => {
    try {
      await editorInstance.editorApp.dispose()
    } catch {
      /* ignore */
    }
    try {
      editorInstance.fsOverlay.dispose()
    } catch {
      /* ignore */
    }
  }

  const ready = createEditor(host, colorMode, uri, document.languageId, document.text)
    .then(async (editorInstance) => {
      if (disposed) {
        await disposeInstance(editorInstance)
        return
      }
      attach(editorInstance)
    })
    .catch((error: unknown) => {
      console.warn('semantifyr-live: the editor failed to start', error)
    })

  return {
    dispose: async () => {
      disposed = true
      for (const unsubscribe of unsubscribes) {
        unsubscribe()
      }
      live.problems.set([])
      await ready
      if (instance) {
        await disposeInstance(instance)
        instance = null
      }
    },
  }
}
