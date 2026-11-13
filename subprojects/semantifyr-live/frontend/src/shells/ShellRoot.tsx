/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { useEffect, useLayoutEffect, useState, useSyncExternalStore } from 'react'
import { parseLiveEditorUrl } from '@semantifyr/editor-common'

import { presentationOf } from '../lib/language/presentation'
import { applyEditorTheme } from '../lib/session/editorTheme'
import { useStore } from '../lib/stores/useStore'
import type { ResolvedColorMode } from '../lib/util/colorMode'
import type { Live } from '../lib/workspace/live'
import { LiveContext } from '../lib/workspace/liveContext'
import DesktopShell from './desktop/DesktopShell'
import EmbedShell from './embed/EmbedShell'
import PhoneShell from './phone/PhoneShell'
import { chooseShell, PHONE_QUERY, type ShellKind } from './route'
import { ShellContext } from './shellContext'
import type { ShellState } from './shellState'
import type { TraceSlot } from './traceSlot'

import '@semantifyr/editor-common/tokens.css'
import '@semantifyr/editor-common/marks.css'
import '../ui/components.css'
import '../ui/base.css'

export interface ShellRootProps {
  live: Live
  shell: ShellState
  colorMode: ResolvedColorMode
  trace?: TraceSlot
}

function useTitle(live: Live): void {
  const documents = useStore(live.documents)
  const title = useStore(live.workspace, (workspace) => {
    const document = workspace.documentKey === null ? undefined : documents.get(workspace.documentKey)
    if (!workspace.flavor || !document) {
      return 'Semantifyr Live'
    }
    const name = document.file?.name ?? (document.shared ? 'Shared model' : document.example.label)
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
  embed: (properties) => {
    return <EmbedShell {...properties} />
  },
  phone: (properties) => {
    return <PhoneShell {...properties} />
  },
}

function usePhoneWidth(): boolean {
  const [query] = useState(() => {
    return window.matchMedia(PHONE_QUERY)
  })
  return useSyncExternalStore((listener) => {
    query.addEventListener('change', listener)
    return () => {
      query.removeEventListener('change', listener)
    }
  }, () => {
    return query.matches
  })
}

// the shells over the same stores; the store wiring and the theme are the same for each
export function ShellRoot({ live, shell, colorMode, trace }: ShellRootProps): React.JSX.Element {
  useLayoutEffect(() => {
    document.documentElement.dataset.theme = colorMode
    void applyEditorTheme(colorMode)
  }, [colorMode])
  useTitle(live)
  const phoneWidth = usePhoneWidth()
  return (
    <LiveContext.Provider value={live}>
      <ShellContext.Provider value={shell}>
        {SHELLS[chooseShell(parseLiveEditorUrl(window.location).view, phoneWidth)]({ colorMode, ...(trace ? { trace } : {}) })}
      </ShellContext.Provider>
    </LiveContext.Provider>
  )
}

