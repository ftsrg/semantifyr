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
import { liveEditorUrl, parseLiveEditorUrl } from '@semantifyr/editor-common'

import { createModelDocuments } from '../lib/session/monaco'
import { SYSML_FLAVOR_ID } from '../lib/session/sysmlBridge'
import { createSettingsStore, createSystemColorModeStore, resolveColorMode, type Settings } from '../lib/stores/settings'
import type { ReadableStore, Store } from '../lib/stores/store'
import { useStore } from '../lib/stores/useStore'
import type { ResolvedColorMode } from '../lib/util/colorMode'
import { frameColorMode, libraryOf, readAddress } from '../lib/workspace/address'
import { createLive, type Live } from '../lib/workspace/live'
import { CaseTrace } from '../trace/CaseTrace'
import { ShellRoot } from './ShellRoot'
import { createShellState } from './shellState'
import { UpdatePrompt } from './UpdatePrompt'

// the address follows the visitor's switches of language and example in place, so Back leaves the page and never shows another model than its address
function useAddress(live: Live): void {
  useEffect(() => {
    let first = true
    let last: string | null = null
    const unsubscribe = live.workspace.subscribe(() => {
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
      void liveEditorUrl(window.location.href, {
        language: flavor.languageId,
        example: document.shared ? undefined : document.example.id,
        library: libraryOf(flavor.id),
        view: parseLiveEditorUrl(window.location).view ?? undefined,
      }).then((url) => {
        window.history.replaceState(null, '', url)
      })
    })
    // a link of another fragment opens in the same page
    const follow = (): void => {
      live.openAddress(window.location.href)
    }
    window.addEventListener('popstate', follow)
    return () => {
      unsubscribe()
      window.removeEventListener('popstate', follow)
    }
  }, [live])
}

function useColorMode(settings: Store<Settings>, system: ReadableStore<ResolvedColorMode>, frame: ResolvedColorMode | null): ResolvedColorMode {
  const theme = useStore(settings, (state) => {
    return state.theme
  })
  const resolved = resolveColorMode(theme, useStore(system))
  return frame ?? resolved
}

export default function ShellApp(): React.JSX.Element {
  const [settings] = useState(createSettingsStore)
  const [system] = useState(createSystemColorModeStore)
  const [shell] = useState(createShellState)
  const [frame] = useState(() => {
    return frameColorMode(readAddress(window.location.href))
  })
  const colorMode = useColorMode(settings, system, frame)
  const [live] = useState(() => {
    return createLive({
      backendUrl: window.location.origin,
      settings,
      colorMode: () => {
        return frame ?? resolveColorMode(settings.get().theme, system.get())
      },
      modelDocuments: createModelDocuments(),
      defaultFlavorId: SYSML_FLAVOR_ID,
      sessionKind: parseLiveEditorUrl(window.location).view ?? 'full',
    })
  })
  useEffect(() => {
    live.start(window.location.href)
    return () => {
      void live.dispose()
    }
  }, [live])
  useAddress(live)
  return (
    <>
      <ShellRoot live={live} shell={shell} colorMode={colorMode} trace={CaseTrace} />
      <UpdatePrompt />
    </>
  )
}
