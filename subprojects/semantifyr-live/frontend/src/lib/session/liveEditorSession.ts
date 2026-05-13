/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import * as vscode from 'vscode'
import type { LanguageClientWrapper } from 'monaco-languageclient/lcwrapper'
import type { EditorApp } from 'monaco-languageclient/editorApp'

import { createApi } from '../api/rest'
import type { FlavorInfo } from '../api/types'
import type { ResolvedColorMode } from '../util/colorMode'
import {
  applyColorTheme,
  connectLanguageClient,
  createEditor,
  createFileUri,
  createSecondaryEditor,
  type EditorInstance,
  type SecondaryEditorHandle,
} from './monaco'
import type { LspClient, VerificationCaseLocation } from '../verification/engine'
import { wrapClientWithMetrics, type LspMetrics, type MetricsTracker } from './lspMetrics'
import { createReconnectController, type ReconnectController } from './reconnectController'
import { SemantifyrLiveApi } from '../api/lspExtensions'

export type LiveEditorStatus =
  | 'initializing'
  | 'connected'
  | 'disconnected'
  | 'reconnecting'
  | 'errored'

export interface ProblemEntry {
  severity: 'error' | 'warning' | 'info' | 'hint'
  startLine: number
  startColumn: number
  endLine: number
  endColumn: number
  message: string
  source: string
}

export interface LiveEditorSessionOptions {
  backendUrl: string
  flavorId: string
  languageId: string
  fileName: string
  initialCode: string
  initialColorMode: ResolvedColorMode
}

export interface LiveEditorSession {
  start(host: HTMLElement): Promise<void>
  dispose(): Promise<void>

  reconnect(): void
  disconnect(): void

  getEditorApp(): EditorApp | null
  getFileUri(): string
  getCurrentCode(): string | null
  getLspClient(): LspClient | undefined
  getLspMetrics(): LspMetrics | null

  readonly api: SemantifyrLiveApi

  setVerifyCaseMarkers(markers: readonly ProblemEntry[]): void
  getProblems(): ProblemEntry[]
  addProblemsListener(cb: () => void): () => void

  addProgressListener(cb: (params: unknown) => void): () => void
  addNotificationListener(method: string, cb: (params: unknown) => void): () => void

  applyColorMode(mode: ResolvedColorMode): Promise<void>
  goToCase(loc: VerificationCaseLocation): void
  revealProblem(p: ProblemEntry): void
  onContentChange(cb: () => void): (() => void) | undefined

  attachReadonlyEditor(
    host: HTMLElement,
    fileUri: string,
    language: string,
  ): Promise<SecondaryEditorHandle>

  onStatusChange(cb: (status: LiveEditorStatus, info?: string) => void): () => void
  onFlavorReady(cb: (flavor: FlavorInfo | null) => void): () => void
}

const MAX_RECONNECT_ATTEMPTS = 3
const RECONNECT_BACKOFF_MS = [1000, 2000, 4000]

interface RawLanguageClient {
  onNotification: (
    method: string,
    cb: (params: unknown) => void,
  ) => { dispose: () => void } | undefined
}

function notifyListeners<T extends (...args: never[]) => void>(
  listeners: Iterable<T>,
  invoke: (cb: T) => void,
  label: string,
): void {
  for (const cb of listeners) {
    try {
      invoke(cb)
    } catch (error) {
      console.warn(`semantifyr-live: ${label} listener threw`, error)
    }
  }
}

function severityFromString(s: ProblemEntry['severity']): vscode.DiagnosticSeverity {
  switch (s) {
    case 'error':
      return vscode.DiagnosticSeverity.Error
    case 'warning':
      return vscode.DiagnosticSeverity.Warning
    case 'info':
      return vscode.DiagnosticSeverity.Information
    case 'hint':
      return vscode.DiagnosticSeverity.Hint
  }
}

function severityToString(s: vscode.DiagnosticSeverity): ProblemEntry['severity'] {
  switch (s) {
    case vscode.DiagnosticSeverity.Error:
      return 'error'
    case vscode.DiagnosticSeverity.Warning:
      return 'warning'
    case vscode.DiagnosticSeverity.Information:
      return 'info'
    case vscode.DiagnosticSeverity.Hint:
      return 'hint'
  }
}

export function createLiveEditorSession(options: LiveEditorSessionOptions): LiveEditorSession {
  const fileUri = createFileUri(options.fileName)

  let editorApp: EditorApp | null = null
  let languageClient: LanguageClientWrapper | null = null
  let fsOverlay: { dispose: () => void } | null = null
  let cancelled = false
  let started = false
  let disposed = false

  let metricsClient: (LspClient & MetricsTracker) | null = null
  let metricsRawClient: LspClient | null = null

  let verifyDiagnostics: vscode.DiagnosticCollection | null = null
  let verifyDiagnosticsSubs: vscode.Disposable[] = []

  let tokenRenderTimer: ReturnType<typeof setTimeout> | null = null

  const progressListeners = new Set<(params: unknown) => void>()
  const problemsListeners = new Set<() => void>()
  const statusListeners = new Set<(status: LiveEditorStatus, info?: string) => void>()
  const flavorListeners = new Set<(flavor: FlavorInfo | null) => void>()
  const notificationHandlers = new Map<string, Set<(params: unknown) => void>>()
  let attachedNotificationDisposables: { dispose: () => void }[] = []

  let lastFlavor: FlavorInfo | null = null

  const reportStatus = (status: LiveEditorStatus, info?: string): void => {
    notifyListeners(statusListeners, (cb) => { cb(status, info); }, 'status')
  }

  const ensureVerifyDiagnostics = (): vscode.DiagnosticCollection | null => {
    if (verifyDiagnostics) {
      return verifyDiagnostics
    }
    try {
      const collection = vscode.languages.createDiagnosticCollection('semantifyr-verify')
      verifyDiagnosticsSubs = [
        vscode.languages.onDidChangeDiagnostics(() => {
          notifyListeners(problemsListeners, (cb) => { cb(); }, 'problems')
        }),
        // Edits invalidate the previous verdict, so wipe the verify markers when the user types.
        vscode.workspace.onDidChangeTextDocument(() => {
          verifyDiagnostics?.clear()
        }),
      ]
      verifyDiagnostics = collection
      return collection
    } catch {
      return null
    }
  }

  const attachNotificationHandlers = (client: LanguageClientWrapper): void => {
    for (const d of attachedNotificationDisposables) {
      try {
        d.dispose()
      } catch {
        /* ignore */
      }
    }
    attachedNotificationDisposables = []
    const raw = client.getLanguageClient() as unknown as RawLanguageClient | undefined
    if (!raw) {
      return
    }
    for (const [method, listeners] of notificationHandlers) {
      try {
        const disposable = raw.onNotification(method, (params) => {
          notifyListeners(listeners, (cb) => { cb(params); }, `notification-${method}`)
        })
        if (disposable) {
          attachedNotificationDisposables.push(disposable)
        }
      } catch (error) {
        console.warn(`semantifyr-live: failed to attach notification listener for ${method}`, error)
      }
    }
  }

  const doConnect = async (): Promise<void> => {
    if (languageClient) {
      try {
        await languageClient.dispose()
      } catch {
        /* ignore */
      }
      languageClient = null
    }
    metricsClient = null
    metricsRawClient = null
    const connected = await connectLanguageClient(
      options.backendUrl,
      options.flavorId,
      options.languageId,
      {
        onProgress: (params: unknown) => {
          notifyListeners(progressListeners, (cb) => { cb(params); }, 'progress')
        },
        onStopped: () => {
          if (cancelled) {
            return
          }
          reconnectController.schedule()
        },
      },
    )
    if (cancelled) {
      try {
        await connected.dispose()
      } catch {
        /* ignore */
      }
      return
    }
    languageClient = connected
    attachNotificationHandlers(connected)

    // Force a repaint once semantic tokens have likely landed; Monaco won't repaint
    // already-painted lines on its own until they re-enter the viewport.
    if (tokenRenderTimer) {
      clearTimeout(tokenRenderTimer)
    }
    tokenRenderTimer = setTimeout(() => {
      tokenRenderTimer = null
      try {
        editorApp?.getEditor()?.render(true)
      } catch {
        /* ignore */
      }
    }, 500)
  }

  const reconnectController: ReconnectController = createReconnectController({
    maxAttempts: MAX_RECONNECT_ATTEMPTS,
    backoffMs: RECONNECT_BACKOFF_MS,
    connect: doConnect,
    isCancelled: () => cancelled,
    onStatus: reportStatus,
  })

  const start = async (host: HTMLElement): Promise<void> => {
    if (started) {
      return
    }
    started = true
    try {
      reportStatus('initializing', 'Initializing editor...')

      let instance: EditorInstance
      try {
        instance = await createEditor(
          host,
          options.initialColorMode,
          fileUri,
          options.languageId,
          options.initialCode,
        )
      } catch {
        if (!cancelled) {
          reportStatus('errored', 'Editor failed to start')
        }
        return
      }
      if (cancelled) {
        try {
          await instance.editorApp.dispose()
        } catch {
          /* ignore */
        }
        try {
          instance.fsOverlay.dispose()
        } catch {
          /* ignore */
        }
        return
      }
      editorApp = instance.editorApp
      fsOverlay = instance.fsOverlay
      void applyColorTheme(options.initialColorMode)

      try {
        await doConnect()
      } catch {
        // eslint-disable-next-line @typescript-eslint/no-unnecessary-condition -- cancelled may flip during await
        if (!cancelled) {
          reportStatus('errored', 'Failed to connect to LSP server')
        }
        return
      }
      // eslint-disable-next-line @typescript-eslint/no-unnecessary-condition -- cancelled may flip during await
      if (cancelled) {
        await dispose()
        return
      }
      reconnectController.resetAttempts()
      reportStatus('connected')

      const api = createApi(options.backendUrl)
      api.fetchFlavor(options.flavorId)
        .then((found) => {
          if (cancelled) {
            return
          }
          lastFlavor = found
          notifyListeners(flavorListeners, (cb) => { cb(found); }, 'flavor')
        })
        .catch(() => { /* non-fatal */ })
    } catch {
      if (!cancelled) {
        reportStatus('errored', 'Editor failed to start')
      }
    }
  }

  const dispose = async (): Promise<void> => {
    if (disposed) {
      return
    }
    disposed = true
    cancelled = true
    reconnectController.cancel()
    if (tokenRenderTimer) {
      clearTimeout(tokenRenderTimer)
      tokenRenderTimer = null
    }
    for (const sub of verifyDiagnosticsSubs) {
      try {
        sub.dispose()
      } catch {
        /* ignore */
      }
    }
    verifyDiagnosticsSubs = []
    try {
      verifyDiagnostics?.dispose()
    } catch {
      /* ignore */
    }
    verifyDiagnostics = null
    try {
      await languageClient?.dispose()
    } catch {
      /* ignore */
    }
    try {
      await editorApp?.dispose()
    } catch {
      /* ignore */
    }
    try {
      fsOverlay?.dispose()
    } catch {
      /* ignore */
    }
    languageClient = null
    editorApp = null
    fsOverlay = null
    metricsClient = null
    metricsRawClient = null
  }

  const reconnect = (): void => {
    if (disposed) {
      return
    }
    cancelled = false
    reconnectController.cancel()
    reconnectController.resetAttempts()
    reportStatus('reconnecting', 'Reconnecting...')
    doConnect()
      .then(() => {
        if (cancelled) {
          return
        }
        reportStatus('connected')
      })
      .catch(() => {
        reconnectController.schedule()
      })
  }

  const disconnect = (): void => {
    if (!languageClient) {
      return
    }
    cancelled = true
    reconnectController.cancel()
    languageClient.dispose().catch(() => { /* ignore */ })
    languageClient = null
    metricsClient = null
    metricsRawClient = null
    reportStatus('disconnected')
  }

  const getLspClient = (): LspClient | undefined => {
    const rawClient = languageClient?.getLanguageClient() as LspClient | undefined
    if (!rawClient) {
      metricsClient = null
      metricsRawClient = null
      return undefined
    }
    if (!metricsClient || metricsRawClient !== rawClient) {
      metricsClient = wrapClientWithMetrics(rawClient)
      metricsRawClient = rawClient
    }
    return metricsClient
  }

  const addNotificationListenerImpl = (
    method: string,
    listener: (params: unknown) => void,
  ): (() => void) => {
    let listeners = notificationHandlers.get(method)
    if (!listeners) {
      listeners = new Set()
      notificationHandlers.set(method, listeners)
      if (languageClient) {
        attachNotificationHandlers(languageClient)
      }
    }
    listeners.add(listener)
    return () => {
      const set = notificationHandlers.get(method)
      if (!set) {
        return
      }
      set.delete(listener)
      if (set.size === 0) {
        notificationHandlers.delete(method)
        if (languageClient) {
          attachNotificationHandlers(languageClient)
        }
      }
    }
  }

  const api = new SemantifyrLiveApi(getLspClient, addNotificationListenerImpl)

  return {
    start,
    dispose,
    reconnect,
    disconnect,
    api,

    getEditorApp: () => editorApp,
    getFileUri: () => fileUri.toString(),
    getCurrentCode: () => editorApp?.getEditor()?.getModel()?.getValue() ?? null,

    getLspClient,

    getLspMetrics: () => metricsClient?.getMetrics() ?? null,

    setVerifyCaseMarkers: (markers) => {
      const collection = ensureVerifyDiagnostics()
      if (!collection) {
        return
      }
      const diagnostics = markers.map((m) => {
        const range = new vscode.Range(
          Math.max(0, m.startLine),
          Math.max(0, m.startColumn),
          Math.max(0, m.endLine),
          Math.max(0, m.endColumn),
        )
        const diagnostic = new vscode.Diagnostic(range, m.message, severityFromString(m.severity))
        diagnostic.source = m.source
        return diagnostic
      })
      collection.set(fileUri, diagnostics)
    },

    getProblems: () => {
      ensureVerifyDiagnostics()
      try {
        const all = vscode.languages.getDiagnostics(fileUri)
        return all.map((d) => ({
          severity: severityToString(d.severity),
          startLine: d.range.start.line,
          startColumn: d.range.start.character,
          endLine: d.range.end.line,
          endColumn: d.range.end.character,
          message: d.message,
          source: d.source ?? '',
        }))
      } catch {
        return []
      }
    },

    addProblemsListener: (cb) => {
      problemsListeners.add(cb)
      return () => {
        problemsListeners.delete(cb)
      }
    },

    addProgressListener: (cb) => {
      progressListeners.add(cb)
      return () => {
        progressListeners.delete(cb)
      }
    },

    addNotificationListener: addNotificationListenerImpl,

    applyColorMode: (mode) => applyColorTheme(mode),

    goToCase: (location) => {
      const editor = editorApp?.getEditor()
      if (!editor) {
        return
      }
      const startLine = location.range.start.line + 1
      const startCol = location.range.start.character + 1
      editor.revealLineInCenter(startLine)
      editor.setPosition({ lineNumber: startLine, column: startCol })
      editor.focus()
    },

    revealProblem: (problem) => {
      const editor = editorApp?.getEditor()
      if (!editor) {
        return
      }
      const startLine = problem.startLine + 1
      const startCol = problem.startColumn + 1
      editor.revealLineInCenter(startLine)
      editor.setPosition({ lineNumber: startLine, column: startCol })
      editor.focus()
    },

    onContentChange: (cb) => {
      const editor = editorApp?.getEditor()
      const disposable = editor?.onDidChangeModelContent(cb)
      return disposable ? () => { disposable.dispose(); } : undefined
    },

    onStatusChange: (cb) => {
      statusListeners.add(cb)
      return () => {
        statusListeners.delete(cb)
      }
    },

    attachReadonlyEditor: async (host, uri, language) => {
      const content = (await api.readDocument(uri)) ?? '// Witness source unavailable.'
      return createSecondaryEditor(host, vscode.Uri.parse(uri), language, content, { readOnly: true })
    },

    onFlavorReady: (cb) => {
      flavorListeners.add(cb)
      if (lastFlavor !== null) {
        notifyListeners([cb], (fn) => { fn(lastFlavor); }, 'flavor')
      }
      return () => {
        flavorListeners.delete(cb)
      }
    },
  }
}
