/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'

import { presentationOf } from '../../lib/language/presentation'
import { isInitialized } from '../../lib/stores/initialization'
import { useStore } from '../../lib/stores/useStore'
import type { ResolvedColorMode } from '../../lib/util/colorMode'
import { caseRowOf, type CaseRow } from '../../lib/workspace/caseRows'
import { useCurrentVerification, useLive } from '../../lib/workspace/liveContext'
import { Icon } from '../../ui/icons'
import { ValidationMark, VerdictMark } from '../../ui/marks'
import { shareLink } from '../desktop/SharePopover'
import { useFlavor } from '../hooks'
import { BackToModel, ModelEditor } from '../modelEditor'
import { embedViewOf } from '../route'
import { useShell } from '../shellContext'
import { verifyAll } from '../shellState'
import { StatusContent, useStatusLine } from '../statusLine'
import { EditControl } from '../touch'
import type { TraceSlot } from '../traceSlot'
import { AddressModel } from './AddressModel'
import { reportHeight } from './height'

import './embed.css'

interface Props {
  colorMode: ResolvedColorMode
  trace?: TraceSlot
}

function VerifyButton(): React.JSX.Element {
  const live = useLive()
  const shell = useShell()
  const verification = useCurrentVerification(live)
  const pending = useStore(live.pendingVerifyAll)
  if (verification.phase === 'running' || pending) {
    return (
      <button
        type="button"
        className="button button-cancel"
        onClick={() => {
          live.cancelVerification()
        }}
      >
        <Icon name="stop" size="small" />
        Cancel
      </button>
    )
  }
  return (
    <button
      type="button"
      className="button button-primary"
      onClick={() => {
        verifyAll(live, shell)
      }}
    >
      <Icon name="play" size="small" />
      Verify all
    </button>
  )
}

function OpenInLive(): React.JSX.Element {
  const live = useLive()
  return (
    <button
      type="button"
      className="button button-quiet open-live"
      title="Opens Semantifyr Live with this model in a new tab"
      onClick={() => {
        // opened within the click, so no popup blocker stops it, and pointed at the link once it is built
        const opened = window.open('', '_blank')
        if (!opened) {
          return
        }
        opened.opener = null
        void shareLink(live).then((link) => {
          if (link === null) {
            opened.close()
          } else {
            opened.location.href = link
          }
        })
      }}
    >
      Open in Live
      <Icon name="external" size="small" />
    </button>
  )
}

function CaseLine({ row, open, onToggle }: { row: CaseRow, open: boolean, onToggle: () => void }): React.JSX.Element {
  const interactive = row.hasTrace
    ? {
      'role': 'button',
      'tabIndex': 0,
      'aria-expanded': open,
      'onClick': onToggle,
      'onKeyDown': (event: React.KeyboardEvent) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          onToggle()
        }
      },
    }
    : {}
  return (
    <div className={`embed-case status-${row.status}${open ? ' is-open' : ''}${row.outdated ? ' is-outdated' : ''}${row.hasTrace ? ' has-trace' : ''}`} data-case={row.id} {...interactive}>
      <VerdictMark status={row.status} label={row.word} />
      <span className="case-name">{row.name}</span>
      <span className="embed-case-verdict">
        {row.validation !== null ? <ValidationMark state={row.validation} detail={row.validationMessage} /> : null}
        <span className="verdict-word">{row.word}</span>
      </span>
      <span className="embed-case-end">
        {row.outdated ? <span className="outdated-tag">Outdated</span> : null}
        {row.time !== null ? <span className="case-time">{row.time}</span> : null}
        {row.hasTrace
          ? (
            <>
              <span className="embed-trace-hint">Trace</span>
              <Icon name="chevron-down" size="small" />
            </>
          )
          : null}
      </span>
      {row.message !== null ? <span className="case-message">{row.message}</span> : null}
    </div>
  )
}

function Cases({ slot }: { slot: TraceSlot | undefined }): React.JSX.Element {
  const live = useLive()
  const flavor = useFlavor(live)
  const verification = useCurrentVerification(live)
  const initialization = useStore(live.initialization)
  const catalog = useStore(live.catalog, (state) => {
    return state.status
  })
  const selection = useStore(live.selection)
  if (catalog !== 'ready' || initialization.steps.length === 0 || !isInitialized(initialization) || !flavor) {
    const text = catalog === 'waiting'
      ? 'The server is not answering. The editor works; the verification cases are listed once a session is up.'
      : `The verification cases are listed once ${flavor ? presentationOf(flavor.languageId).name : 'the language'} has initialized.`
    return <div className="embed-initializing">{text}</div>
  }
  if (verification.cases.length === 0) {
    return <div className="embed-initializing">The model has no verification cases.</div>
  }
  const Slot = slot
  return (
    <>
      {verification.cases.map((caseState) => {
        const row = caseRowOf(caseState)
        const open = row.hasTrace && selection === row.id
        return (
          <div key={row.id} className="embed-case-group">
            <CaseLine
              row={row}
              open={open}
              onToggle={() => {
                live.selectCase(open ? null : row.id)
              }}
            />
            {open
              ? (
                <div className={`embed-trace status-${row.status}${row.outdated ? ' is-outdated' : ''}`} aria-label={`Trace of ${row.name}`}>
                  {Slot ? <Slot caseState={caseState} flavor={flavor} /> : null}
                </div>
              )
              : null}
          </div>
        )
      })}
    </>
  )
}

function Strip({ slot }: { slot: TraceSlot | undefined }): React.JSX.Element {
  const live = useLive()
  const line = useStatusLine()
  const catalog = useStore(live.catalog, (state) => {
    return state.status
  })
  const initializing = useStore(live.initialization, (state) => {
    return state.steps.length === 0 || !isInitialized(state)
  })
  return (
    <div className="embed-strip">
      {initializing && catalog !== 'waiting' ? <div className="progress-indeterminate" /> : null}
      <div className="strip-bar">
        <VerifyButton />
        <span className={`strip-status is-${line.status.kind}`} role="status" data-testid="status-message">
          <StatusContent line={line} />
        </span>
        <OpenInLive />
      </div>
      <div className="embed-cases" data-testid="case-list">
        <Cases slot={slot} />
      </div>
    </div>
  )
}

// the frame's page is told the embed's height whenever it changes, so the frame grows with the trace
function useReportedHeight(): React.RefObject<HTMLDivElement | null> {
  const root = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const element = root.current
    if (!element) {
      return
    }
    let last = -1
    const observer = new ResizeObserver(() => {
      const height = Math.ceil(element.getBoundingClientRect().height)
      if (height !== last) {
        last = height
        reportHeight(height)
      }
    })
    observer.observe(element)
    return () => {
      observer.disconnect()
    }
  }, [])
  return root
}

export default function EmbedShell({ colorMode, trace }: Props): React.JSX.Element {
  const live = useLive()
  const languageId = useStore(live.workspace, (workspace) => {
    return workspace.flavor?.languageId
  })
  const [view] = useState(() => {
    return embedViewOf(window.location.search) ?? 'verify'
  })
  const [contentHeight, setContentHeight] = useState<number | null>(null)
  const fit = useCallback((height: number) => {
    setContentHeight(height)
  }, [])
  const root = useReportedHeight()
  useLayoutEffect(() => {
    document.documentElement.dataset.shell = 'embed'
    return () => {
      delete document.documentElement.dataset.shell
    }
  }, [])
  const editOnly = view === 'edit-only'
  return (
    <div ref={root} className={`embed${editOnly ? ' is-edit-only' : ''}`} data-language={languageId} data-testid="embed-shell">
      <BackToModel />
      <div className="embed-editor" style={editOnly && contentHeight !== null ? { height: contentHeight } : undefined}>
        {languageId === undefined ? <AddressModel /> : <ModelEditor colorMode={colorMode} verdictMarks={!editOnly} {...(editOnly ? { onContentHeight: fit } : {})} />}
        <EditControl className="embed-edit-control" />
      </div>
      {editOnly ? null : <Strip slot={trace} />}
    </div>
  )
}
