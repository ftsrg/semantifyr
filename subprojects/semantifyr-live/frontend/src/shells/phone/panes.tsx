/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { type VerdictStatus, LanguageMark, VerdictMark } from '@semantifyr/editor-common/marks'

import { parseLiveEditorUrl } from '@semantifyr/editor-common'
import { findExample } from '../../examples'
import { initializationStepsOf, presentationOf } from '../../lib/language/presentation'
import { areCasesKnown, initializationOf } from '../../lib/stores/initialization'
import { useStore } from '../../lib/stores/useStore'
import type { ResolvedColorMode } from '../../lib/util/colorMode'
import { caseRowOf, countsOf, type CaseCounts, type CaseRow } from '../../lib/workspace/caseRows'
import { useCurrentVerification, useLive } from '../../lib/workspace/liveContext'
import { stepTimeText } from '../../lib/workspace/shellStatus'
import { casesSentence, EXAMPLE_PAGE, exampleRights, noCases, SERVER_NOT_ANSWERING, VERDICT_WORDS, VERIFY_ALL_HELD, websitePage } from '../../texts'
import { Icon } from '../../ui/icons'
import { ValidationMark } from '../../ui/marks'
import { addressProblemText } from '../desktop/Orientation'
import { documentLabel, useCurrentDocument, useFlavor, useNow } from '../hooks'
import { BackToModel, ModelEditor } from '../modelEditor'
import { useShell } from '../shellContext'
import { dismissOrientation } from '../shellState'
import { EditControl, useTouchInput } from '../touch'
import type { TraceSlot } from '../traceSlot'

export type Pane =
  | 'model'
  | 'results'
  | 'trace'

export function AddressError(): React.JSX.Element | null {
  const live = useLive()
  const problem = useStore(live.address)
  if (!problem) {
    return null
  }
  return (
    <div className="phone-orientation is-error" role="alert">
      <Icon name="alert" size="small" />
      <span>{addressProblemText(live, problem)}</span>
      <button
        type="button"
        className="phone-icon-button"
        title="Dismiss"
        aria-label="Dismiss"
        onClick={() => {
          live.address.set(null)
        }}
      >
        <Icon name="close" size="small" />
      </button>
    </div>
  )
}

function Orientation(): React.JSX.Element | null {
  const live = useLive()
  const shell = useShell()
  const document = useCurrentDocument(live)
  const flavor = useFlavor(live)
  const verification = useCurrentVerification(live)
  const dismissed = useStore(shell.dismissedOrientations)
  if (!document || !flavor || document.shared || dismissed.has(document.key)) {
    return null
  }
  const example = findExample(flavor, document.example.id)
  if (!example) {
    return null
  }
  const count = verification.discovery === 'discovered' ? verification.cases.length : null
  // once the model was verified, by the visitor or by the link that opened it, the button needs no pointing at
  const verified = verification.phase !== 'idle' || verification.cases.some((caseState) => {
    return caseState.resultStatus !== undefined
  })
  return (
    <div className="phone-orientation">
      <span className="orientation-mark" />
      <span className="orientation-text">
        <strong>{`${example.label}.`}</strong>
        {casesSentence(count)}
        {verified
          ? null
          : (
            <>
              {' Press '}
              <strong>Verify all</strong>
              {count === 1 ? ' to check it.' : ' to check them.'}
            </>
          )}
        {example.served
          ? (
            <span className="phone-orientation-source">
              {` ${exampleRights(example.served.origin, example.served.holder, example.served.licence)} `}
              <a className="link" href={websitePage(example.served.page)} target="_blank" rel="noreferrer">{EXAMPLE_PAGE}</a>
            </span>
          )
          : null}
      </span>
      <button
        type="button"
        className="phone-icon-button"
        title="Dismiss"
        aria-label="Dismiss"
        onClick={() => {
          dismissOrientation(shell, document.key)
        }}
      >
        <Icon name="close" size="small" />
      </button>
    </div>
  )
}

function ModelBar(): React.JSX.Element | null {
  const live = useLive()
  const shell = useShell()
  const flavor = useFlavor(live)
  const document = useCurrentDocument(live)
  const touch = useTouchInput()
  const editing = useStore(shell.editing)
  if (!flavor || !document) {
    return <div className="model-bar" />
  }
  const label = documentLabel(document, flavor)
  if (touch && editing) {
    return (
      <div className="model-bar is-editing">
        <Icon name="edit" size="small" />
        <span className="model-bar-name">{`Editing ${label}`}</span>
        <EditControl className="model-bar-button" />
      </div>
    )
  }
  return (
    <div className="model-bar">
      <LanguageMark languageId={flavor.languageId} />
      <span className="model-bar-name">{label}</span>
      {touch ? <span className="model-bar-hint">Read-only</span> : null}
      <EditControl className="model-bar-button" />
    </div>
  )
}

// stays mounted behind the other panes, so the editor keeps its document, its undo history and its view
export function ModelPane({ colorMode, hidden }: { colorMode: ResolvedColorMode, hidden: boolean }): React.JSX.Element {
  const live = useLive()
  const shell = useShell()
  const flavor = useFlavor(live)
  const editing = useStore(shell.editing)
  return (
    <section className={`pane pane-model${editing ? ' is-editing' : ''}`} data-language={flavor?.languageId} aria-label="Model" hidden={hidden}>
      {editing ? null : <Orientation />}
      <ModelBar />
      <BackToModel />
      <div className="pane-editor">
        <ModelEditor colorMode={colorMode} verdictMarks wrapping />
      </div>
    </section>
  )
}

const COUNT_GROUPS: readonly { status: VerdictStatus & keyof CaseCounts, label: string }[] = [
  { status: 'passed', label: VERDICT_WORDS.passed },
  { status: 'failed', label: VERDICT_WORDS.failed },
  { status: 'inconclusive', label: VERDICT_WORDS.inconclusive },
  { status: 'error', label: VERDICT_WORDS.error },
]

function Counts({ counts }: { counts: CaseCounts }): React.JSX.Element {
  return (
    <span className="counts">
      {COUNT_GROUPS.map(({ status, label }) => {
        const value = counts[status]
        return (
          <span key={status} className={`count status-${status}${value === 0 ? ' is-zero' : ''}`} title={`${label}: ${value}`} aria-label={`${label}: ${value}`}>
            <VerdictMark status={status} />
            <span>{value}</span>
          </span>
        )
      })}
      <span className="counts-total">{`of ${counts.total}`}</span>
    </span>
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
  const note = waiting
    ? SERVER_NOT_ANSWERING
    : failedStep?.reason ?? (failed ? 'The verification cases are listed once a session has found them.' : null)
  return (
    <div className="phone-init" data-language={languageId}>
      <div className="phone-init-title">
        <LanguageMark languageId={languageId} />
        {`Initializing ${presentation.name}`}
      </div>
      <ul className="phone-init-steps">
        {steps.map((step) => {
          const state = step.state === 'done' ? 'is-done' : step.state === 'running' ? 'is-current' : step.state === 'failed' ? 'is-failed' : ''
          return (
            <li key={step.id} className={`phone-init-step ${state}`}>
              {step.state === 'done' ? <Icon name="check" /> : null}
              {step.state === 'failed' ? <Icon name="alert" /> : null}
              {step.state === 'running' ? <VerdictMark status="running" /> : null}
              {step.state === 'pending' ? <VerdictMark status="not-run" /> : null}
              <span>{step.label}</span>
              <span className="phone-init-time">{stepTimeText(step, now, waiting ? 'retrying' : 'failed')}</span>
            </li>
          )
        })}
      </ul>
      {failed ? null : <div className="progress-indeterminate" />}
      {note === null ? null : <p className="phone-init-note">{note}</p>}
      {queued
        ? (
          <div className="phone-init-pressed">
            <Icon name="clock" />
            <span>{VERIFY_ALL_HELD}</span>
          </div>
        )
        : null}
    </div>
  )
}

function CaseRowView({ row, selected, onOpen }: { row: CaseRow, selected: boolean, onOpen: (row: CaseRow) => void }): React.JSX.Element {
  const live = useLive()
  const waiting = row.waiting
  return (
    <div
      className={`phone-case status-${row.status}${row.outdated ? ' is-outdated' : ''}${selected ? ' is-selected' : ''}`}
      role="button"
      tabIndex={0}
      data-case={row.id}
      onClick={() => {
        onOpen(row)
      }}
      onKeyDown={(event) => {
        if (event.target === event.currentTarget && (event.key === 'Enter' || event.key === ' ')) {
          event.preventDefault()
          onOpen(row)
        }
      }}
    >
      <VerdictMark status={row.status} label={row.word} />
      <div className="phone-case-main">
        <div className="case-name">{row.name}</div>
        <div className="phone-case-outcome">
          {row.validation !== null ? <ValidationMark state={row.validation} detail={row.validationMessage} /> : null}
          <span className="verdict-word">{row.word}</span>
          {row.time !== null ? <span className="case-time">{row.time}</span> : null}
          {row.outdated ? <span className="outdated-tag">Outdated</span> : null}
        </div>
        {row.message !== null ? <div className="case-message">{row.message}</div> : null}
      </div>
      <span className="phone-case-side">
        <button
          type="button"
          className="phone-case-run"
          title={waiting ? 'Cancel this case' : 'Run this case'}
          aria-label={`${waiting ? 'Cancel' : 'Run'} ${row.name}`}
          onClick={(event) => {
            event.stopPropagation()
            if (waiting) {
              live.cancelCase(row.id)
            } else {
              live.verifyCase(row.id)
            }
          }}
        >
          <Icon name={waiting ? 'stop' : 'play'} />
        </button>
        {row.hasTrace ? <Icon name="chevron-right" /> : null}
      </span>
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

export function ResultsPane({ onPane }: { onPane: (pane: Pane) => void }): React.JSX.Element {
  const live = useLive()
  const flavor = useFlavor(live)
  const verification = useCurrentVerification(live)
  const selection = useStore(live.selection)
  const listed = useCasesListed()
  const rows = verification.cases.map(caseRowOf)
  // a case with a trace opens it; one without shows its definition
  const open = (row: CaseRow): void => {
    live.selectCase(row.id)
    if (row.hasTrace) {
      onPane('trace')
      return
    }
    const caseState = verification.cases.find((candidate) => {
      return candidate.caseInfo.id === row.id
    })
    if (caseState) {
      onPane('model')
      live.revealRange(caseState.caseInfo.location.range)
    }
  }
  return (
    <section className="pane pane-results" aria-label="Results">
      <div className="pane-scroll">
        {listed
          ? (
            <>
              <div className="pane-header">
                <Counts counts={countsOf(rows)} />
              </div>
              <div role="list" aria-label="Verification cases" data-testid="case-list">
                {rows.map((row) => {
                  return <CaseRowView key={row.id} row={row} selected={row.id === selection} onOpen={open} />
                })}
              </div>
              {rows.length === 0 ? <p className="pane-hint">{noCases(flavor?.languageId ?? '')}</p> : null}
            </>
          )
          : <Initialization />}
      </div>
    </section>
  )
}

export function TracePane({ slot }: { slot: TraceSlot | undefined }): React.JSX.Element {
  const live = useLive()
  const flavor = useFlavor(live)
  const verification = useCurrentVerification(live)
  const selection = useStore(live.selection)
  const caseState = verification.cases.find((candidate) => {
    return candidate.caseInfo.id === selection
  })
  const row = caseState ? caseRowOf(caseState) : undefined
  if (!caseState || !row || !row.hasTrace || !flavor) {
    return (
      <section className="pane pane-trace" aria-label="Trace">
        <p className="pane-hint">Open a case with a trace to see it here.</p>
      </section>
    )
  }
  const Slot = slot
  return (
    <section className={`pane pane-trace status-${row.status}${row.outdated ? ' is-outdated' : ''}`} aria-label="Trace">
      <div className="trace-head">
        <div className="trace-head-title">
          <VerdictMark status={row.status} label={row.word} />
          <span className="case-name">{row.name}</span>
        </div>
        <div className="trace-head-meta">
          <span className="verdict-word">{row.word}</span>
          {row.time !== null ? <span>{row.time}</span> : null}
          {row.outdated ? <span className="outdated-tag">Outdated</span> : null}
        </div>
      </div>
      <div className="trace-body">
        {Slot ? <Slot caseState={caseState} flavor={flavor} wrapping /> : null}
      </div>
    </section>
  )
}
