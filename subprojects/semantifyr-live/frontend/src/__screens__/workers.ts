/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import editorWorker from '@codingame/monaco-vscode-editor-api/esm/vs/editor/editor.worker.js?worker&url'
import extensionHostWorker from '@codingame/monaco-vscode-api/workers/extensionHost.worker?worker&url'
import textMateWorker from '@codingame/monaco-vscode-textmate-service-override/worker?worker&url'

const WORKERS: Readonly<Record<string, string>> = {
  editorWorkerService: editorWorker,
  extensionHostWorkerMain: extensionHostWorker,
  TextMateWorker: textMateWorker,
}

// the test server does not resolve the worker addresses the language client builds, so the screens take Vite's
const environment = globalThis as unknown as { MonacoEnvironment?: { getWorker?: (workerId: string, label: string) => Worker } }
environment.MonacoEnvironment = {
  ...environment.MonacoEnvironment,
  getWorker: (_workerId, label) => {
    const url = WORKERS[label]
    if (url === undefined) {
      throw new Error(`no worker for ${label}`)
    }
    return new Worker(url, { type: 'module' })
  },
}
