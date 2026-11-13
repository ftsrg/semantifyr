/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { useEffect, useState } from 'react'
import { liveEditorUrl, parseLiveEditorUrl } from '@semantifyr/editor-common'

import { createModelDocuments } from '../lib/session/monaco'
import { SYSML_FLAVOR_ID } from '../lib/session/sysmlBridge'
import { createSettingsStore, createSystemColorModeStore, resolveColorMode, type Settings } from '../lib/stores/settings'
import type { ReadableStore, Store } from '../lib/stores/store'
import { useStore } from '../lib/stores/useStore'
import type { ResolvedColorMode } from '../lib/util/colorMode'
import { frameColorMode, historyChangeOf, libraryOf, readAddress } from '../lib/workspace/address'
import { createLive, type Live } from '../lib/workspace/live'
import { CaseTrace } from '../trace/CaseTrace'
import { ShellRoot } from './ShellRoot'
import { createShellState } from './shellState'
import { UpdatePrompt } from './UpdatePrompt'

// the address follows the visitor's switches of language and example, and Back and Forward open the address they land on
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
      const address = parseLiveEditorUrl(window.location)
      void liveEditorUrl(window.location.href, {
        language: flavor.languageId,
        example: document.shared ? undefined : document.example.id,
        library: libraryOf(flavor.id),
        view: address.view ?? undefined,
        frame: address.frame ?? undefined,
      }).then((url) => {
        switch (historyChangeOf(window.location.href, url, document.shared)) {
          case 'push':
            window.history.pushState(null, '', url)
            break
          case 'replace':
            window.history.replaceState(null, '', url)
            break
          case null:
            break
        }
      })
    })
    // Back, Forward and a link of another fragment open their address in the same page
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
