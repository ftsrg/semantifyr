/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { useRef } from 'react'

import { parseLiveEditorUrl } from '@semantifyr/editor-common'
import { initializationStepsOf, presentationOf } from '../../lib/language/presentation'
import { areCasesKnown, initializationOf } from '../../lib/stores/initialization'
import { changeSettings } from '../../lib/stores/settings'
import { useStore } from '../../lib/stores/useStore'
import { countsOf, STATUS_WORDS, type CaseCounts, type CaseRow, type RowStatus } from '../../lib/workspace/caseRows'
import { openModelOxsts } from '../../lib/workspace/libraryTabs'
import { useCurrentVerification, useLive } from '../../lib/workspace/liveContext'
import type { Live } from '../../lib/workspace/live'
import { secondsToRetry, stepTimeText } from '../../lib/workspace/shellStatus'
import { Icon } from '../../ui/icons'
import { LanguageMark, ValidationMark, VerdictMark } from '../../ui/marks'
import { documentLabel, useCurrentDocument, useFlavor, useNow } from '../hooks'
import { useShell } from '../shellContext'
import { verifyAll, type CountFilter } from '../shellState'
import { clamp, ColumnSplitter, KEYBOARD_STEP, useDrag } from '../splitter'
import type { TraceSlot } from '../traceSlot'
import { SharedNotice, useCaseRows } from './keeping'
import { SettingsPopover } from './SettingsPopover'

import './rerun.css'

const COUNT_GROUPS: readonly { group: CountFilter, status: RowStatus, label: string }[] = [
  { group: 'passed', status: 'passed', label: 'Passed' },
  { group: 'failed', status: 'failed', label: 'Failed' },
  { group: 'inconclusive', status: 'inconclusive', label: 'Inconclusive' },
  { group: 'error', status: 'error', label: 'Error' },
  { group: 'waiting', status: 'running', label: 'Running or queued' },
]

const COLUMN_MINIMUM = 360
const COLUMN_MAXIMUM = 900
const LIST_MINIMUM = 40

function groupOf(status: RowStatus): CountFilter | null {
  switch (status) {
    case 'running':
    case 'queued':
      return 'waiting'
    case 'not-run':
      return null
    default:
      return status
  }
}

function countOf(counts: CaseCounts, group: CountFilter): number {
  return counts[group]
}

function Counts({ counts, compact }: { counts: CaseCounts, compact: boolean }): React.JSX.Element {
  const shell = useShell()
  const filter = useStore(shell.filter)
  return (
    <span className="counts">
      {COUNT_GROUPS
        .filter(({ group }) => {
          return group !== 'waiting' || counts.waiting > 0
        })
        .map(({ group, status, label }) => {
          const count = countOf(counts, group)
          return (
            <button
              key={group}
              type="button"
              className={`count status-${status}${count === 0 ? ' is-zero' : ''}`}
              aria-pressed={filter === group}
              title={`${label}: ${count}`}
              aria-label={`${label}: ${count}`}
              onClick={() => {
                shell.filter.set(filter === group ? null : group)
              }}
            >
              <VerdictMark status={status} />
              <span>{count}</span>
            </button>
          )
        })}
      {compact ? null : <span className="counts-total">{`of ${counts.total}`}</span>}
    </span>
  )
}

function useBusy(live: Live): boolean {
  const verification = useCurrentVerification(live)
  const queued = useStore(live.queuedVerification)
  return verification.phase === 'running' || queued !== null
}

function VerifyButton({ compact }: { compact: boolean }): React.JSX.Element {
  const live = useLive()
  const shell = useShell()
  const busy = useBusy(live)
  if (busy) {
    return (
      <button
        type="button"
        className={`button button-cancel${compact ? ' rail-verify' : ''}`}
        title={compact ? 'Cancel' : undefined}
        aria-label="Cancel"
        onClick={() => {
          live.cancelVerification()
        }}
      >
        <Icon name="stop" size={compact ? undefined : 'small'} />
        {compact ? null : 'Cancel'}
      </button>
    )
  }
  return (
    <button
      type="button"
      className={`button button-primary${compact ? ' rail-verify' : ''}`}
      title={compact ? 'Verify all' : undefined}
      aria-label="Verify all"
      onClick={() => {
        verifyAll(live, shell)
      }}
    >
      <Icon name="play" size={compact ? undefined : 'small'} />
      {compact ? null : 'Verify all'}
    </button>
  )
}

function ModelOxstsButton({ compact }: { compact: boolean }): React.JSX.Element | null {
  const live = useLive()
  const flavor = useFlavor(live)
  const document = useCurrentDocument(live)
  const runtime = useStore(live.workspace, (workspace) => {
    return workspace.runtime
  })
  if (!flavor || !document || !runtime || flavor.languageId === 'oxsts') {
    return null
  }
  return (
    <button
      type="button"
      className={`button model-oxsts-button ${compact ? 'button-icon' : 'button-small button-quiet'}`}
      title="Open the OXSTS of the model"
      aria-label="Open the OXSTS of the model"
      onClick={() => {
        openModelOxsts(live, runtime, documentLabel(document, flavor))
      }}
    >
      <LanguageMark languageId="oxsts" />
      {compact ? null : 'OXSTS'}
    </button>
  )
}

// the language the address will open, while the server has not answered with its languages
function addressedLanguage(): string {
  return parseLiveEditorUrl(window.location).language ?? 'sysml'
}

function Initialization(): React.JSX.Element {
  const live = useLive()
  const catalog = useStore(live.catalog, (state) => {
    return state.status
  })
  const flavor = useFlavor(live)
  const runtimeSteps = useStore(live.initialization)
  const connection = useStore(live.connection)
  const queued = useStore(live.queuedVerification)
  const now = useNow(1000)
  const languageId = flavor?.languageId ?? addressedLanguage()
  const presentation = presentationOf(languageId)
  const waiting = catalog === 'waiting'
  const steps = flavor
    ? runtimeSteps.steps
    : initializationOf(initializationStepsOf(languageId, window.location.origin)).steps.map((step, index) => {
      return index === 0 ? { ...step, state: waiting ? 'failed' as const : 'running' as const, startedAt: now } : step
    })
  const failedStep = steps.find((step) => {
    return step.state === 'failed'
  })
  const failed = failedStep !== undefined
  const retrying = secondsToRetry(connection, now)
  const trouble = waiting ? 'The server is not answering.' : connection.refused ? 'The server refused the session: it is at capacity.' : retrying === null ? null : 'The connection was lost.'
  const note = waiting
    ? 'The server is not answering. The model can be edited and its verification cases are known once it answers.'
    : trouble !== null
      ? `${trouble} The editor works; the verification cases are known once a session is up.`
    : failedStep?.reason ?? (failed ? 'The verification cases are listed once a session has found them.' : null)
  const failedWord = waiting ? 'retrying' : retrying ? `retrying in ${retrying} s` : retrying === 0 ? 'retrying' : 'failed'
  return (
    <div className="initialization" data-language={languageId}>
      <div className="initialization-title">
        <LanguageMark languageId={languageId} />
        {`Initializing ${presentation.name}`}
      </div>
      <ul className="initialization-steps">
        {steps.map((step) => {
          const state = step.state === 'done' ? 'is-done' : step.state === 'running' ? 'is-current' : step.state === 'failed' ? 'is-failed' : ''
          return (
            <li key={step.id} className={`initialization-step ${state}`}>
              {step.state === 'done' ? <Icon name="check" /> : null}
              {step.state === 'failed' ? <Icon name="alert" /> : null}
              {step.state === 'running' ? <VerdictMark status="running" /> : null}
              {step.state === 'pending' ? <VerdictMark status="not-run" /> : null}
              <span>{step.label}</span>
              <span className="initialization-step-time">{stepTimeText(step, now, failedWord)}</span>
            </li>
          )
        })}
      </ul>
      {failed ? null : <div className="progress-indeterminate" />}
      {note === null ? null : <div className="initialization-note">{note}</div>}
      {queued
        ? (
          <div className="initialization-pressed">
            <Icon name="clock" />
            <span>Verify all is pressed. Every case runs as soon as initialization has finished.</span>
          </div>
        )
        : null}
    </div>
  )
}

function CaseRowView({ row, selected, canValidate }: { row: CaseRow, selected: boolean, canValidate: boolean }): React.JSX.Element {
  const live = useLive()
  const waiting = row.waiting
  const select = (): void => {
    live.selectCase(row.id)
    const caseState = live.verification.get().get(live.workspace.get().documentKey ?? '')?.cases.find((candidate) => {
      return candidate.caseInfo.id === row.id
    })
    if (caseState) {
      live.revealRange(caseState.caseInfo.location.range)
    }
  }
  return (
    <div
      className={`case-row status-${row.status}${selected ? ' is-selected' : ''}${row.outdated ? ' is-outdated' : ''}${row.shared ? ' is-shared' : ''}${waiting ? ' is-waiting' : ''}`}
      role="row"
      tabIndex={0}
      aria-selected={selected}
      data-case={row.id}
      title={`Go to the definition of ${row.name}`}
      onClick={select}
      onKeyDown={(event) => {
        if (event.target !== event.currentTarget) {
          return
        }
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          select()
        }
        if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
          event.preventDefault()
          const sibling = event.key === 'ArrowDown' ? event.currentTarget.nextElementSibling : event.currentTarget.previousElementSibling
          if (sibling instanceof HTMLElement) {
            sibling.focus()
          }
        }
      }}
    >
      <VerdictMark status={row.status} label={row.word} />
      <span className="case-name">{row.name}</span>
      <span className="case-outcome">
        {row.outdated ? <span className="outdated-tag">Outdated</span> : null}
        {row.shared ? <span className="shared-tag">Shared</span> : null}
        {row.differs !== null
          ? (
            <span className="differs-tag" title={`The shared verdict was ${row.differs}`}>
              <Icon name="alert" size="tiny" />
              {`Differs, shared: ${row.differs}`}
            </span>
          )
          : null}
        {row.validation !== null ? <ValidationMark state={row.validation} detail={row.validationMessage} /> : null}
        <span className="verdict-word">{row.word}</span>
        {row.time !== null ? <span className="case-time">{row.time}</span> : null}
        {row.rerun !== null
          ? (
            <span className={`rerun-tag status-${row.rerun}`}>
              <VerdictMark status={row.rerun} />
              {STATUS_WORDS[row.rerun]}
            </span>
          )
          : null}
      </span>
      <span className="case-actions">
        {waiting
          ? (
            <button
              type="button"
              className="case-action"
              title="Cancel this case"
              aria-label={`Cancel ${row.name}`}
              onClick={(event) => {
                event.stopPropagation()
                live.cancelCase(row.id)
              }}
            >
              <Icon name="stop" />
            </button>
          )
          : (
            <>
              <button
                type="button"
                className="case-action"
                title="Run this case"
                aria-label={`Run ${row.name}`}
                onClick={(event) => {
                  event.stopPropagation()
                  live.verifyCase(row.id)
                }}
              >
                <Icon name="play" />
              </button>
              {row.hasTrace && canValidate && row.validation !== 'validating'
                ? (
                  <button
                    type="button"
                    className="case-action"
                    title={row.validation === null ? 'Validate' : 'Validate again'}
                    aria-label={`${row.validation === null ? 'Validate' : 'Validate again'} ${row.name}`}
                    onClick={(event) => {
                      event.stopPropagation()
                      live.validateWitness(row.id)
                    }}
                  >
                    <Icon name="shield-check" />
                  </button>
                )
                : null}
            </>
          )}
      </span>
      {row.message !== null ? <div className="case-message">{row.message}</div> : null}
    </div>
  )
}

function CaseList({ rows }: { rows: readonly CaseRow[] }): React.JSX.Element {
  const live = useLive()
  const shell = useShell()
  const flavor = useFlavor(live)
  const selection = useStore(live.selection)
  const filter = useStore(shell.filter)
  const height = useStore(live.settings, (settings) => {
    return settings.caseListHeight
  })
  const shown = rows.filter((row) => {
    return filter === null || groupOf(row.status) === filter
  })
  return (
    <>
      {filter !== null
        ? (
          <div className="results-notice is-filter">
            <span>{`Showing ${shown.length} of ${rows.length}: ${COUNT_GROUPS.find((entry) => {
              return entry.group === filter
            })?.label.toLowerCase() ?? ''}`}</span>
            <button
              type="button"
              className="button-link"
              onClick={() => {
                shell.filter.set(null)
              }}
            >
              Show all
            </button>
          </div>
        )
        : null}
      <div className="case-list" role="grid" aria-label="Verification cases" style={height > 0 ? { height, maxHeight: 'none' } : undefined} data-testid="case-list">
        {shown.map((row) => {
          return <CaseRowView key={row.id} row={row} selected={row.id === selection} canValidate={Boolean(flavor?.validateWitnessCommand)} />
        })}
      </div>
    </>
  )
}

function emptyHint(rows: readonly CaseRow[], selected: CaseRow | undefined): string {
  if (selected?.status === 'passed') {
    return 'Passed, and nothing to show: no state of the model fails the case.'
  }
  if (selected?.status === 'failed') {
    return 'No trace came back for this case.'
  }
  if (rows.some((row) => {
    return row.status !== 'not-run' && row.status !== 'running' && row.status !== 'queued'
  })) {
    return 'Select a case to see its trace, where it has one.'
  }
  return 'Press Verify all. Each case is answered Passed or Failed, and where a run shows why, its trace appears here in the language of the model.'
}

function TraceArea({ rows, slot }: { rows: readonly CaseRow[], slot: TraceSlot | undefined }): React.JSX.Element {
  const live = useLive()
  const flavor = useFlavor(live)
  const verification = useCurrentVerification(live)
  const selection = useStore(live.selection)
  const selected = rows.find((row) => {
    return row.id === selection
  })
  const caseState = verification.cases.find((candidate) => {
    return candidate.caseInfo.id === selection
  })
  if (!selected || !caseState || !flavor || !selected.hasTrace) {
    return (
      <section className="trace" aria-label="Trace">
        <div className="empty-hint">{emptyHint(rows, selected)}</div>
      </section>
    )
  }
  const Slot = slot
  return (
    <section className={`trace status-${selected.status}${selected.outdated ? ' is-outdated' : ''}`} aria-label="Trace">
      <div className="trace-head">
        <div className="trace-title">
          <span className="section-label">Trace</span>
          <span className="case-name">{selected.name}</span>
        </div>
      </div>
      {Slot ? <Slot caseState={caseState} flavor={flavor} readOnlyTabs /> : null}
    </section>
  )
}

function ResultsSplitter({ column }: { column: React.RefObject<HTMLElement | null> }): React.JSX.Element {
  const live = useLive()
  const width = useRef(0)
  const startDrag = useDrag((event) => {
    width.current = clamp(window.innerWidth - event.clientX, COLUMN_MINIMUM, COLUMN_MAXIMUM)
    column.current?.parentElement?.style.setProperty('--results-width', `${width.current}px`)
  }, () => {
    changeSettings(live.settings, { resultsColumnWidth: width.current })
  })
  return (
    <ColumnSplitter
      label="Width of the results column"
      startDrag={startDrag}
      reset={() => {
        column.current?.parentElement?.style.removeProperty('--results-width')
        changeSettings(live.settings, { resultsColumnWidth: 0 })
      }}
      resize={(by) => {
        const current = column.current?.getBoundingClientRect().width ?? COLUMN_MINIMUM
        changeSettings(live.settings, { resultsColumnWidth: clamp(Math.round(current + by), COLUMN_MINIMUM, COLUMN_MAXIMUM) })
      }}
    />
  )
}

function ListSplitter({ column }: { column: React.RefObject<HTMLElement | null> }): React.JSX.Element {
  const live = useLive()
  const height = useRef(0)
  const list = (): HTMLElement | null => {
    return column.current?.querySelector<HTMLElement>('.case-list') ?? null
  }
  const startDrag = useDrag((event) => {
    const element = list()
    if (!element) {
      return
    }
    height.current = Math.max(LIST_MINIMUM, Math.round(event.clientY - element.getBoundingClientRect().top))
    element.style.height = `${height.current}px`
    element.style.maxHeight = 'none'
  }, () => {
    changeSettings(live.settings, { caseListHeight: height.current })
  })
  const resize = (by: number): void => {
    const current = list()?.getBoundingClientRect().height ?? LIST_MINIMUM
    changeSettings(live.settings, { caseListHeight: Math.max(LIST_MINIMUM, Math.round(current + by)) })
  }
  return (
    <div
      className="splitter splitter-horizontal"
      role="separator"
      aria-orientation="horizontal"
      aria-label="Height of the case list"
      tabIndex={0}
      title="Drag to resize; double-click to reset"
      onPointerDown={startDrag}
      onDoubleClick={() => {
        const element = list()
        element?.style.removeProperty('height')
        element?.style.removeProperty('max-height')
        changeSettings(live.settings, { caseListHeight: 0 })
      }}
      onKeyDown={(event) => {
        if (event.key === 'ArrowUp') {
          resize(-KEYBOARD_STEP)
        }
        if (event.key === 'ArrowDown') {
          resize(KEYBOARD_STEP)
        }
      }}
    >
      <span />
    </div>
  )
}

function Rail({ counts, listed }: { counts: CaseCounts, listed: boolean }): React.JSX.Element {
  const live = useLive()
  return (
    <aside className="rail" aria-label="Verification cases">
      <button
        type="button"
        className="button button-icon"
        title="Show the verification cases"
        aria-label="Show the verification cases"
        onClick={() => {
          changeSettings(live.settings, { resultsColumnCollapsed: false })
        }}
      >
        <Icon name="panel-expand" />
      </button>
      <VerifyButton compact />
      <ModelOxstsButton compact />
      <span className="rail-divider" />
      {listed ? <Counts counts={counts} compact /> : <VerdictMark status="running" label="Initializing" />}
    </aside>
  )
}

export function ResultsColumn({ trace }: { trace: TraceSlot | undefined }): React.JSX.Element {
  const live = useLive()
  const initialization = useStore(live.initialization)
  const catalog = useStore(live.catalog, (state) => {
    return state.status
  })
  const collapsed = useStore(live.settings, (settings) => {
    return settings.resultsColumnCollapsed
  })
  const column = useRef<HTMLElement>(null)
  const listed = catalog === 'ready' && areCasesKnown(initialization)
  const { rows, results, running } = useCaseRows()
  const counts = countsOf(rows)

  if (collapsed) {
    return <Rail counts={counts} listed={listed} />
  }
  return (
    <aside className="results" ref={column} aria-label="Verification cases">
      <div className="results-header">
        <button
          type="button"
          className="button button-icon"
          title="Collapse the panel"
          aria-label="Collapse the panel"
          onClick={() => {
            changeSettings(live.settings, { resultsColumnCollapsed: true })
          }}
        >
          <Icon name="panel-collapse" />
        </button>
        {listed ? <Counts counts={counts} compact={false} /> : <span className="results-initializing-label">Initializing</span>}
        <span className="results-spacer" />
        <ModelOxstsButton compact={false} />
        <SettingsPopover />
        <VerifyButton compact={false} />
      </div>
      {listed
        ? (
          <>
            {results ? <SharedNotice rows={rows} results={results} running={running} /> : null}
            <CaseList rows={rows} />
            <ListSplitter column={column} />
            <TraceArea rows={rows} slot={trace} />
          </>
        )
        : <Initialization />}
      <ResultsSplitter column={column} />
    </aside>
  )
}
