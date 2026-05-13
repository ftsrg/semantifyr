/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { forwardRef, useEffect, useImperativeHandle, useRef } from 'react'

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

export interface LiveEditorHandle {
  reconnect: () => void
  disconnect: () => Promise<void> | undefined
  goToCase: (location: VerificationCaseLocation) => void
  getLspClient: () => LspClient | undefined
  getLspMetrics: () => LspMetrics | null
  getApi: () => SemantifyrLiveApi | null
  getFileUri: () => string
  getCurrentCode: () => string | null
  onEditorContentChange: (callback: () => void) => (() => void) | undefined
  addProgressListener: (listener: (params: unknown) => void) => () => void
  addNotificationListener: (method: string, listener: (params: unknown) => void) => () => void
  setVerifyCaseMarkers: (markers: readonly ProblemEntry[]) => void
  getProblems: () => ProblemEntry[]
  addProblemsListener: (listener: () => void) => () => void
  revealProblem: (problem: ProblemEntry) => void
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

  // Subscribers attach at session-start, so they read latest callbacks via a ref.
  const onStatusChangeRef = useRef(onStatusChange)
  const onFlavorReadyRef = useRef(onFlavorReady)
  useEffect(() => {
    onStatusChangeRef.current = onStatusChange
    onFlavorReadyRef.current = onFlavorReady
  })

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
