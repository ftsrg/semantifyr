/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { parseLiveEditorUrl } from '@semantifyr/editor-common'

import type { LiveFlavor } from '../../examples'
import { presentationOf } from '../../lib/language/presentation'
import { isInitialized } from '../../lib/stores/initialization'
import { useStore } from '../../lib/stores/useStore'
import type { ResolvedColorMode } from '../../lib/util/colorMode'
import type { VerificationCaseState } from '../../lib/verification/engine'
import { fixedFrame } from '../../lib/workspace/address'
import { caseRowOf, type CaseRow } from '../../lib/workspace/caseRows'
import { useCurrentVerification, useLive } from '../../lib/workspace/liveContext'
import { Icon } from '../../ui/icons'
import { ValidationMark, VerdictMark } from '../../ui/marks'
import { addressProblemText } from '../desktop/Orientation'
import { shareLink } from '../desktop/SharePopover'
import { useFlavor } from '../hooks'
import { BackToModel, ModelEditor } from '../modelEditor'
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
      title="Opens Semantifyr Live with this model and its results in a new tab"
      onClick={() => {
        // opened within the click, so no popup blocker stops it, and pointed at the link once it is built
        const opened = window.open('', '_blank')
        if (!opened) {
          return
        }
        opened.opener = null
        void shareLink(live, true).then((link) => {
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

// a name of the address that was not found, and what opened in its place
function AddressError(): React.JSX.Element | null {
  const live = useLive()
  const problem = useStore(live.address)
  if (!problem) {
    return null
  }
  return (
    <div className="embed-address-error" role="alert">
      <Icon name="alert" size="small" />
      <span>{addressProblemText(live, problem)}</span>
    </div>
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

function useCasesListed(): boolean {
  const live = useLive()
  const catalog = useStore(live.catalog, (state) => {
    return state.status
  })
  const initialized = useStore(live.initialization, (state) => {
    return state.steps.length > 0 && isInitialized(state)
  })
  return catalog === 'ready' && initialized
}

// in a growing frame the trace text is as tall as it is and wraps; a fixed frame scrolls it in its area
function CaseTraceArea({ caseState, row, flavor, slot, growing }: { caseState: VerificationCaseState, row: CaseRow, flavor: LiveFlavor, slot: TraceSlot | undefined, growing: boolean }): React.JSX.Element {
  const Slot = slot
  return (
    <div className={`embed-trace status-${row.status}${row.outdated ? ' is-outdated' : ''}`} aria-label={`Trace of ${row.name}`}>
      {Slot ? <Slot caseState={caseState} flavor={flavor} growing={growing} wrapping={growing} /> : null}
    </div>
  )
}

// a fixed frame shows the open case's trace below every case line, in the room the lines leave
function FixedTrace({ slot }: { slot: TraceSlot | undefined }): React.JSX.Element | null {
  const live = useLive()
  const flavor = useFlavor(live)
  const verification = useCurrentVerification(live)
  const selection = useStore(live.selection)
  const listed = useCasesListed()
  const caseState = verification.cases.find((candidate) => {
    return candidate.caseInfo.id === selection
  })
  if (!listed || !caseState || !flavor) {
    return null
  }
  const row = caseRowOf(caseState)
  return row.hasTrace ? <CaseTraceArea caseState={caseState} row={row} flavor={flavor} slot={slot} growing={false} /> : null
}

function Cases({ slot, inlineTrace }: { slot: TraceSlot | undefined, inlineTrace: boolean }): React.JSX.Element {
  const live = useLive()
  const flavor = useFlavor(live)
  const verification = useCurrentVerification(live)
  const listed = useCasesListed()
  const catalog = useStore(live.catalog, (state) => {
    return state.status
  })
  const selection = useStore(live.selection)
  if (!listed || !flavor) {
    const text = catalog === 'waiting'
      ? 'The server is not answering. The editor works; the verification cases are listed once a session is up.'
      : `The verification cases are listed once ${flavor ? presentationOf(flavor.languageId).name : 'the language'} has initialized.`
    return <div className="embed-initializing">{text}</div>
  }
  if (verification.cases.length === 0) {
    return <div className="embed-initializing">The model has no verification cases.</div>
  }
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
            {open && inlineTrace ? <CaseTraceArea caseState={caseState} row={row} flavor={flavor} slot={slot} growing /> : null}
          </div>
        )
      })}
    </>
  )
}

function Strip({ slot, fixed }: { slot: TraceSlot | undefined, fixed: boolean }): React.JSX.Element {
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
        <Cases slot={slot} inlineTrace={!fixed} />
      </div>
      {fixed ? <FixedTrace slot={slot} /> : null}
    </div>
  )
}

// the frame's page is told the embed's height whenever it changes, so the frame grows with the trace
function useReportedHeight(reported: boolean): React.RefObject<HTMLDivElement | null> {
  const root = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const element = root.current
    if (!element || !reported) {
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
  }, [reported])
  return root
}

export default function EmbedShell({ colorMode, trace }: Props): React.JSX.Element {
  const live = useLive()
  const languageId = useStore(live.workspace, (workspace) => {
    return workspace.flavor?.languageId
  })
  const [address] = useState(() => {
    return parseLiveEditorUrl(window.location)
  })
  const view = address.view ?? 'embed'
  const fixed = fixedFrame(address)
  const [contentHeight, setContentHeight] = useState<number | null>(null)
  const fit = useCallback((height: number) => {
    setContentHeight(height)
  }, [])
  const root = useReportedHeight(!fixed)
  useLayoutEffect(() => {
    document.documentElement.dataset.shell = 'embed'
    return () => {
      delete document.documentElement.dataset.shell
    }
  }, [])
  const editOnly = view === 'edit-only'
  const fitted = editOnly && !fixed
  return (
    <div ref={root} className={`embed${editOnly ? ' is-edit-only' : ''}${fixed ? ' is-fixed' : ''}`} data-language={languageId} data-testid="embed-shell">
      <AddressError />
      <BackToModel />
      <div className="embed-editor" style={fitted && contentHeight !== null ? { height: contentHeight } : undefined}>
        {languageId === undefined ? <AddressModel /> : <ModelEditor colorMode={colorMode} verdictMarks={!editOnly} {...(fitted ? { onContentHeight: fit } : {})} />}
        <EditControl className="embed-edit-control" />
      </div>
      {editOnly ? null : <Strip slot={trace} fixed={fixed} />}
    </div>
  )
}
