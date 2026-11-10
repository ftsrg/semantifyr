/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'

import { useStore } from '../../lib/stores/useStore'
import type { ResolvedColorMode } from '../../lib/util/colorMode'
import { useLive } from '../../lib/workspace/liveContext'
import type { TraceSlot } from '../traceSlot'
import { EditorArea } from './EditorArea'
import { Orientation } from './Orientation'
import { ResultsColumn } from './ResultsColumn'
import { StatusBar } from './StatusBar'
import { Toolbar } from './Toolbar'

import './desktop.css'
import './shell.css'
import './tablet.css'

interface Props {
  colorMode: ResolvedColorMode
  trace?: TraceSlot
}

export default function DesktopShell({ colorMode, trace }: Props): React.JSX.Element {
  const live = useLive()
  const settings = useStore(live.settings)
  const languageId = useStore(live.workspace, (workspace) => {
    return workspace.flavor?.languageId
  })
  const columnStyle = settings.resultsColumnWidth > 0 && !settings.resultsColumnCollapsed
    ? { '--results-width': `${settings.resultsColumnWidth}px` } as React.CSSProperties
    : undefined
  return (
    <div className="app" data-language={languageId} data-testid="desktop-shell">
      <Toolbar />
      <Orientation />
      <main className={`workspace${settings.resultsColumnCollapsed ? ' is-collapsed' : ''}`} style={columnStyle}>
        <EditorArea colorMode={colorMode} />
        <ResultsColumn trace={trace} />
      </main>
      <StatusBar />
    </div>
  )
}
