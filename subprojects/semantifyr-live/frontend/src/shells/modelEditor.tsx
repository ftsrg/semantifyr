/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { useEffect, useRef } from 'react'

import type { LiveFlavor } from '../examples'
import { setLibraryReader } from '../lib/session/monaco'
import { bindTabbedEditor } from '../lib/session/tabbedEditor'
import { useStore } from '../lib/stores/useStore'
import type { ResolvedColorMode } from '../lib/util/colorMode'
import type { DocumentRecord } from '../lib/workspace/live'
import { useLive, useRuntime } from '../lib/workspace/liveContext'
import { Icon } from '../ui/icons'
import { useCurrentDocument, useFlavor } from './hooks'
import { useShell } from './shellContext'
import { modelLockOf } from './touch'

interface EditorOptions {
  verdictMarks: boolean
  wrapping?: boolean
  onContentHeight?: (height: number) => void
}

function EditorHost({ document, flavor, colorMode, options }: { document: DocumentRecord, flavor: LiveFlavor, colorMode: ResolvedColorMode, options: EditorOptions }): React.JSX.Element {
  const live = useLive()
  const shell = useShell()
  const hostReference = useRef<HTMLDivElement>(null)
  const mountedWith = useRef({ document, colorMode, options })

  useEffect(() => {
    const host = hostReference.current
    if (!host) {
      return
    }
    const { document: mounted, colorMode: mode, options: mountedOptions } = mountedWith.current
    const binding = bindTabbedEditor({
      host,
      live,
      document: { uri: mounted.uri, languageId: flavor.languageId, text: live.currentText() ?? mounted.text },
      colorMode: mode,
      onCursor: (position) => {
        shell.cursor.set(position)
      },
      modelLocked: modelLockOf(shell),
      verdictMarks: mountedOptions.verdictMarks,
      wrapping: mountedOptions.wrapping === true,
      ...(mountedOptions.onContentHeight ? { onContentHeight: mountedOptions.onContentHeight } : {}),
    })
    return () => {
      void binding.dispose()
    }
  }, [live, shell, flavor.languageId])

  return <div ref={hostReference} className="model-editor-host" data-testid="editor-host" />
}

// the model in the one editor of the embed and the phone, which have no tabs
export function ModelEditor({ colorMode, verdictMarks, wrapping, onContentHeight }: { colorMode: ResolvedColorMode } & EditorOptions): React.JSX.Element | null {
  const live = useLive()
  const flavor = useFlavor(live)
  const document = useCurrentDocument(live)
  const runtime = useRuntime(live)
  useEffect(() => {
    if (!runtime) {
      return
    }
    return setLibraryReader(runtime.readLibrary)
  }, [runtime])
  if (!flavor || !document) {
    return null
  }
  return <EditorHost key={flavor.languageId} document={document} flavor={flavor} colorMode={colorMode} options={{ verdictMarks, wrapping: wrapping === true, ...(onContentHeight ? { onContentHeight } : {}) }} />
}

// a read-only document the editor opened, a library's definition or a trace's text, and the way back
export function BackToModel(): React.JSX.Element | null {
  const live = useLive()
  const label = useStore(live.tabs.state, (state) => {
    return state.tabs.find((tab) => {
      return tab.id === state.active
    })?.label ?? null
  })
  if (label === null) {
    return null
  }
  return (
    <div className="readonly-banner">
      <Icon name="lock" />
      <span className="readonly-banner-label">{`${label}, read-only`}</span>
      <button
        type="button"
        className="button-link"
        onClick={() => {
          live.tabs.show(null)
        }}
      >
        Back to the model
      </button>
    </div>
  )
}
