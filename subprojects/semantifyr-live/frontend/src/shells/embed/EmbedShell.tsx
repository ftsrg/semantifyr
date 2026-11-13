/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { Fragment, useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { parseLiveEditorUrl } from '@semantifyr/editor-common'
import { VerdictMark } from '@semantifyr/editor-common/marks'

import type { LiveFlavor } from '../../examples'
import { presentationOf } from '../../lib/language/presentation'
import { areCasesKnown, isInitialized } from '../../lib/stores/initialization'
import { useStore } from '../../lib/stores/useStore'
import type { ResolvedColorMode } from '../../lib/util/colorMode'
import type { VerificationCaseState } from '../../lib/verification/engine'
import { fixedFrame } from '../../lib/workspace/address'
import { caseRowOf, type CaseRow } from '../../lib/workspace/caseRows'
import { useCurrentVerification, useLive } from '../../lib/workspace/liveContext'
import { Icon } from '../../ui/icons'
import { ValidationMark } from '../../ui/marks'
import { addressProblemText } from '../desktop/Orientation'
import { shareLink } from '../desktop/SharePopover'
import { useFlavor } from '../hooks'
import { BackToModel, ModelEditor } from '../modelEditor'
import { useShell } from '../shellContext'
import { verifyAll } from '../shellState'
import { clamp, ColumnSplitter, useDrag } from '../splitter'
import { StatusContent, useStatusLine } from '../statusLine'
import { EditControl } from '../touch'
import type { TraceSlot } from '../traceSlot'
import { AddressModel } from './AddressModel'
import { reportHeight, reportShown } from './height'

import './embed.css'

interface Props {
  colorMode: ResolvedColorMode
  trace?: TraceSlot
}

function VerifyButton(): React.JSX.Element {
  const live = useLive()
  const shell = useShell()
  const verification = useCurrentVerification(live)
  const queued = useStore(live.queuedVerification) !== null
  if (verification.phase === 'running' || queued) {
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

// a fixed frame wraps a case name where one of its words starts
function breakable(name: string): React.ReactNode {
  return name.split(/(?<=[a-z0-9])(?=[A-Z])|(?<=[._:])(?=[^._:])/).map((part, index) => {
    return (
      <Fragment key={index}>
        {index > 0 ? <wbr /> : null}
        {part}
      </Fragment>
    )
  })
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
      <span className="case-name">{breakable(row.name)}</span>
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
  const known = useStore(live.initialization, (state) => {
    return areCasesKnown(state)
  })
  return catalog === 'ready' && known
}

// in a growing frame the trace text is as tall as it is; a fixed frame scrolls it in its area, under a title naming the case, which its row may be scrolled away from
function CaseTraceArea({ caseState, row, flavor, slot, growing }: { caseState: VerificationCaseState, row: CaseRow, flavor: LiveFlavor, slot: TraceSlot | undefined, growing: boolean }): React.JSX.Element {
  const Slot = slot
  return (
    <div className={`embed-trace status-${row.status}${row.outdated ? ' is-outdated' : ''}`} aria-label={`Trace of ${row.name}`}>
      {growing
        ? null
        : (
          <div className="embed-trace-title" data-testid="trace-title">
            <span className="section-label">Trace</span>
            <VerdictMark status={row.status} label={row.word} />
            <span className="case-name">{breakable(row.name)}</span>
          </div>
        )}
      {Slot ? <Slot caseState={caseState} flavor={flavor} growing={growing} wrapping /> : null}
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
      ? 'The server is not answering. The model can be edited and its verification cases are listed once it answers.'
      : `The verification cases are listed once ${flavor ? `the ${presentationOf(flavor.languageId).name}` : 'the'} language server has found them.`
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

const EDITOR_MINIMUM = 280
const RESULTS_MINIMUM = 320

// in a frame too narrow for both minimums the even split stays
function resultsWidth(embed: HTMLElement, wanted: number): number | null {
  const frame = embed.getBoundingClientRect().width
  return frame < EDITOR_MINIMUM + RESULTS_MINIMUM ? null : Math.round(clamp(wanted, RESULTS_MINIMUM, frame - EDITOR_MINIMUM))
}

function EmbedSplitter({ root }: { root: React.RefObject<HTMLDivElement | null> }): React.JSX.Element {
  const shell = useShell()
  const width = useRef<number | null>(null)
  const drag = useDrag((event) => {
    const embed = root.current
    if (!embed) {
      return
    }
    width.current = resultsWidth(embed, embed.getBoundingClientRect().right - event.clientX)
    if (width.current !== null) {
      embed.style.setProperty('--embed-results-width', `${width.current}px`)
    }
  }, () => {
    if (width.current !== null) {
      shell.embedResultsWidth.set(width.current)
    }
  })
  // a press without a move, as in a double click, leaves the split as it is
  const startDrag = (event: React.PointerEvent<HTMLElement>): void => {
    width.current = null
    drag(event)
  }
  return (
    <ColumnSplitter
      label="Width of the results"
      startDrag={startDrag}
      reset={() => {
        root.current?.style.removeProperty('--embed-results-width')
        shell.embedResultsWidth.set(null)
      }}
      resize={(by) => {
        const embed = root.current
        const current = embed?.querySelector('.embed-strip')?.getBoundingClientRect().width
        const next = embed && current !== undefined ? resultsWidth(embed, current + by) : null
        if (next !== null) {
          shell.embedResultsWidth.set(next)
        }
      }}
    />
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
    const observer = new ResizeObserver((entries) => {
      const height = Math.ceil(entries.at(-1)?.borderBoxSize[0]?.blockSize ?? 0)
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
  const shell = useShell()
  const split = useStore(shell.embedResultsWidth)
  useLayoutEffect(() => {
    document.documentElement.dataset.shell = 'embed'
    return () => {
      delete document.documentElement.dataset.shell
    }
  }, [])
  const editOnly = view === 'edit-only'
  const openAt = fixed ? null : address.lines
  const fitted = (editOnly || openAt !== null) && !fixed
  return (
    <div
      ref={root}
      className={`embed${editOnly ? ' is-edit-only' : ''}${fixed ? ' is-fixed' : ''}`}
      data-language={languageId}
      data-testid="embed-shell"
      style={split === null ? undefined : { '--embed-results-width': `${split}px` } as React.CSSProperties}
    >
      <AddressError />
      <BackToModel />
      <div className="embed-editor" style={fitted && contentHeight !== null ? { height: contentHeight } : undefined}>
        {languageId === undefined ? <AddressModel /> : <ModelEditor colorMode={colorMode} verdictMarks={!editOnly} onShown={reportShown} {...(fitted ? { onContentHeight: fit } : {})} {...(openAt ? { openAt } : {})} />}
        <EditControl className="embed-edit-control" />
      </div>
      {editOnly ? null : <Strip slot={trace} fixed={fixed} />}
      {fixed && !editOnly ? <EmbedSplitter root={root} /> : null}
    </div>
  )
}
