/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import '@fontsource/jetbrains-mono/latin-400.css'
import '@fontsource/jetbrains-mono/latin-ext-400.css'
import '@fontsource/jetbrains-mono/latin-600.css'
import '@fontsource/jetbrains-mono/latin-ext-600.css'
import type React from 'react'
import { useEffect, useLayoutEffect, useState } from 'react'
import { liveEditorUrl } from '@semantifyr/editor-common'

import { presentationOf } from '../lib/language/presentation'
import { applyEditorTheme } from '../lib/session/editorTheme'
import { createModelDocuments } from '../lib/session/monaco'
import { SYSML_FLAVOR_ID } from '../lib/session/sysmlBridge'
import { createSettingsStore, createSystemColorModeStore, resolveColorMode, type Settings } from '../lib/stores/settings'
import type { ReadableStore, Store } from '../lib/stores/store'
import { useStore } from '../lib/stores/useStore'
import type { ResolvedColorMode } from '../lib/util/colorMode'
import { createLive, type Live } from '../lib/workspace/live'
import { LiveContext } from '../lib/workspace/liveContext'
import DesktopShell from './desktop/DesktopShell'
import { chooseShell, type ShellKind } from './route'
import { ShellContext } from './shellContext'
import { createShellState, type ShellState } from './shellState'
import type { TraceSlot } from './traceSlot'
import { UpdatePrompt } from './UpdatePrompt'

import '../ui/tokens.css'
import '../ui/components.css'
import '../ui/base.css'

export interface ShellRootProps {
  live: Live
  shell: ShellState
  colorMode: ResolvedColorMode
  trace?: TraceSlot
}

// the address follows the visitor's switches of language and example, as today's page does
function useAddress(live: Live): void {
  useEffect(() => {
    let first = true
    let last: string | null = null
    return live.workspace.subscribe(() => {
      const { flavor, documentKey } = live.workspace.get()
      const document = documentKey === null ? undefined : live.documents.get().get(documentKey)
      if (!flavor || !document || documentKey === last) {
        return
      }
      last = documentKey
      if (first) {
        first = false
        return
      }
      void liveEditorUrl(window.location.href, { flavor: flavor.id, example: document.example.id }).then((url) => {
        window.history.replaceState(null, '', url)
      })
    })
  }, [live])
}

function useTitle(live: Live): void {
  const title = useStore(live.workspace, (workspace) => {
    const document = workspace.documentKey === null ? undefined : live.documents.get().get(workspace.documentKey)
    if (!workspace.flavor || !document) {
      return 'Semantifyr Live'
    }
    const name = document.shared ? 'Shared model' : document.example.label
    return `${name} - ${presentationOf(workspace.flavor.languageId).name} - Semantifyr Live`
  })
  useEffect(() => {
    document.title = title
  }, [title])
}

const SHELLS: Readonly<Record<ShellKind, (properties: { colorMode: ResolvedColorMode, trace?: TraceSlot }) => React.JSX.Element>> = {
  desktop: (properties) => {
    return <DesktopShell {...properties} />
  },
}

// the shells over the same stores; the store wiring and the theme are the same for each
export function ShellRoot({ live, shell, colorMode, trace }: ShellRootProps): React.JSX.Element {
  useLayoutEffect(() => {
    document.documentElement.dataset.theme = colorMode
    void applyEditorTheme(colorMode)
  }, [colorMode])
  useTitle(live)
  return (
    <LiveContext.Provider value={live}>
      <ShellContext.Provider value={shell}>
        {SHELLS[chooseShell()]({ colorMode, ...(trace ? { trace } : {}) })}
      </ShellContext.Provider>
    </LiveContext.Provider>
  )
}

function useColorMode(settings: Store<Settings>, system: ReadableStore<ResolvedColorMode>): ResolvedColorMode {
  const theme = useStore(settings, (state) => {
    return state.theme
  })
  return resolveColorMode(theme, useStore(system))
}

export default function ShellApp(): React.JSX.Element {
  const [settings] = useState(createSettingsStore)
  const [system] = useState(createSystemColorModeStore)
  const [shell] = useState(createShellState)
  const colorMode = useColorMode(settings, system)
  const [live] = useState(() => {
    return createLive({
      backendUrl: window.location.origin,
      settings,
      colorMode: () => {
        return resolveColorMode(settings.get().theme, system.get())
      },
      modelDocuments: createModelDocuments(),
      defaultFlavorId: SYSML_FLAVOR_ID,
      sessionKind: 'full',
    })
  })
  useEffect(() => {
    live.start(window.location.search)
    return () => {
      void live.dispose()
    }
  }, [live])
  useAddress(live)
  return (
    <>
      <ShellRoot live={live} shell={shell} colorMode={colorMode} />
      <UpdatePrompt />
    </>
  )
}
