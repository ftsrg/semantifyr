/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React, { forwardRef, useEffect, useImperativeHandle, useRef } from 'react'

import type { FlavorInfo } from '../../lib/api'
import type { ResolvedColorMode as ColorMode } from '../../lib/util/colorMode'
import {
  createLiveEditorSession,
  type LiveEditorSession,
  type LiveEditorStatus,
  type ProblemEntry,
} from '../../lib/session/liveEditorSession'
import type { SecondaryEditorHandle } from '../../lib/session/monaco'
import type { LspClient, VerificationCaseLocation } from '../../lib/verification'
import type { LspMetrics } from '../../lib/session/lspMetrics'
import type { SemantifyrLiveApi } from '../../lib/api/lspExtensions'

import styles from './LiveEditor.module.css'

export type { LiveEditorStatus, ProblemEntry }

/**
 * Imperative handle the parent uses to drive the editor (verify markers, focus a problem,
 * read LSP metrics, etc). The methods are 1-to-1 with {@link LiveEditorSession}; the React
 * component is a thin wrapper that owns the host {@code <div>} and the session lifecycle.
 */
export interface LiveEditorHandle {
  reconnect: () => void
  disconnect: () => void
  goToCase: (location: VerificationCaseLocation) => void
  getLspClient: () => LspClient | undefined
  getLspMetrics: () => LspMetrics | null
  /**
   * Typed wrapper around the live-server's custom JSON-RPC methods (session info,
   * active-verifications monitor, cancel). Stable across reconnects.
   */
  getApi: () => SemantifyrLiveApi | null
  getFileUri: () => string
  /** Returns the current editor model content, or {@code null} when the editor is not up. */
  getCurrentCode: () => string | null
  onEditorContentChange: (callback: () => void) => (() => void) | undefined
  addProgressListener: (listener: (params: unknown) => void) => () => void
  addNotificationListener: (method: string, listener: (params: unknown) => void) => () => void
  setVerifyCaseMarkers: (markers: readonly ProblemEntry[]) => void
  getProblems: () => ProblemEntry[]
  addProblemsListener: (listener: () => void) => () => void
  revealProblem: (problem: ProblemEntry) => void
  /** Mount a read-only Monaco editor sharing this session's LSP. Used by the witness Raw view. */
  attachReadonlyEditor: (
    host: HTMLElement,
    fileUri: string,
    language: string,
  ) => Promise<SecondaryEditorHandle>
}

interface Props {
  flavorId: string
  languageId: string
  fileName: string
  initialCode: string
  backendUrl: string
  colorMode: ColorMode
  fillParent?: boolean
  onStatusChange?: (status: LiveEditorStatus, info?: string) => void
  onFlavorReady?: (flavor: FlavorInfo | null) => void
}

const LiveEditor = forwardRef<LiveEditorHandle, Props>(function LiveEditor(
  {
    flavorId,
    languageId,
    fileName,
    initialCode,
    backendUrl,
    colorMode,
    fillParent = false,
    onStatusChange,
    onFlavorReady,
  },
  ref,
): React.JSX.Element {
  const hostRef = useRef<HTMLDivElement>(null)
  const sessionRef = useRef<LiveEditorSession | null>(null)

  // Keep a fresh ref to the latest event callbacks. Subscribers attached at session-start
  // time cannot capture later prop versions on their own, so they read through the ref.
  const onStatusChangeRef = useRef(onStatusChange)
  const onFlavorReadyRef = useRef(onFlavorReady)
  useEffect(() => {
    onStatusChangeRef.current = onStatusChange
    onFlavorReadyRef.current = onFlavorReady
  })

  // Single bootstrap effect. Re-runs when any session input changes (flavor / language /
  // file / initialCode / backend); each re-run disposes the prior session and starts a fresh
  // one. The parent forces this by changing the React `key` on the LiveEditor element when
  // it wants a clean restart.
  useEffect(() => {
    const session = createLiveEditorSession({
      backendUrl,
      flavorId,
      languageId,
      fileName,
      initialCode,
      initialColorMode: colorMode,
    })
    sessionRef.current = session
    const unsubStatus = session.onStatusChange((status, info) => onStatusChangeRef.current?.(status, info))
    const unsubFlavor = session.onFlavorReady((flavor) => onFlavorReadyRef.current?.(flavor))
    if (hostRef.current) {
      void session.start(hostRef.current)
    }
    return () => {
      unsubStatus()
      unsubFlavor()
      sessionRef.current = null
      void session.dispose()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [flavorId, languageId, fileName, backendUrl, initialCode])

  // Color-mode changes apply to whatever session is current; no remount needed.
  useEffect(() => {
    void sessionRef.current?.applyColorMode(colorMode)
  }, [colorMode])

  useImperativeHandle(ref, () => ({
    reconnect: () => sessionRef.current?.reconnect(),
    disconnect: () => sessionRef.current?.disconnect(),
    goToCase: (location) => sessionRef.current?.goToCase(location),
    getLspClient: () => sessionRef.current?.getLspClient(),
    getLspMetrics: () => sessionRef.current?.getLspMetrics() ?? null,
    getApi: () => sessionRef.current?.api ?? null,
    getFileUri: () => sessionRef.current?.getFileUri() ?? '',
    getCurrentCode: () => sessionRef.current?.getCurrentCode() ?? null,
    onEditorContentChange: (cb) => sessionRef.current?.onContentChange(cb),
    addProgressListener: (cb) => sessionRef.current?.addProgressListener(cb) ?? (() => {}),
    addNotificationListener: (method, cb) =>
      sessionRef.current?.addNotificationListener(method, cb) ?? (() => {}),
    setVerifyCaseMarkers: (markers) => sessionRef.current?.setVerifyCaseMarkers(markers),
    getProblems: () => sessionRef.current?.getProblems() ?? [],
    addProblemsListener: (cb) => sessionRef.current?.addProblemsListener(cb) ?? (() => {}),
    revealProblem: (p) => sessionRef.current?.revealProblem(p),
    attachReadonlyEditor: async (host, uri, language) => {
      const session = sessionRef.current
      if (!session) {
        // Disposable no-op when the session isn't up - matches the React lifecycle where the
        // tab can request an attach before the session has finished bootstrapping.
        return { dispose: () => {} }
      }
      return session.attachReadonlyEditor(host, uri, language)
    },
  }), [])

  const panelClassName = fillParent
    ? `${styles.editorPanel} ${styles.editorPanelFill}`
    : styles.editorPanel
  return <div ref={hostRef} className={panelClassName} />
})

export default LiveEditor
