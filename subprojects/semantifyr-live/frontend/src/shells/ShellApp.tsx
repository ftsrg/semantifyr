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
import { useEffect, useState } from 'react'
import { liveEditorUrl } from '@semantifyr/editor-common'

import { createModelDocuments } from '../lib/session/monaco'
import { SYSML_FLAVOR_ID } from '../lib/session/sysmlBridge'
import { createSettingsStore, createSystemColorModeStore, resolveColorMode, type Settings } from '../lib/stores/settings'
import type { ReadableStore, Store } from '../lib/stores/store'
import { useStore } from '../lib/stores/useStore'
import type { ResolvedColorMode } from '../lib/util/colorMode'
import { createLive, type Live } from '../lib/workspace/live'
import { ShellRoot } from './ShellRoot'
import { createShellState } from './shellState'
import { UpdatePrompt } from './UpdatePrompt'

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
