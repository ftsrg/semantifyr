/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { Popover } from '@base-ui/react/popover'

import { useStore } from '../../lib/stores/useStore'
import { CLOSING_LINE_MS, shellStatus, type ShellStatus } from '../../lib/workspace/shellStatus'
import { useCurrentVerification, useLive } from '../../lib/workspace/liveContext'
import { Icon } from '../../ui/icons'
import { LanguageMark, VerdictMark } from '../../ui/marks'
import { useFlavor, useLanguage, useNow } from '../hooks'
import { useShell } from '../shellContext'
import { UnsavedLine } from './keeping'
import { flavourText } from './Toolbar'

function StatusIcon({ status }: { status: ShellStatus }): React.JSX.Element | null {
  switch (status.kind) {
    case 'busy':
      return <VerdictMark status="running" />
    case 'warning':
    case 'problem':
      return <Icon name="alert" />
    case 'done':
      return <Icon name="check" />
    case 'idle':
      return null
  }
}

function StatusMessage(): React.JSX.Element {
  const live = useLive()
  const shell = useShell()
  const catalog = useStore(live.catalog, (state) => {
    return state.status
  })
  const connection = useStore(live.connection)
  const initialization = useStore(live.initialization)
  const verification = useCurrentVerification(live)
  const timing = useStore(live.runTiming)
  const blocked = useStore(shell.verifyBlocked)
  const problems = useStore(live.problems)
  const language = useLanguage(live)
  const closing = timing !== null && timing.finishedAt !== null && Date.now() - timing.finishedAt < CLOSING_LINE_MS
  const now = useNow(closing || connection.retryAt !== null ? 1000 : null)
  const unsaved = useStore(live.unsaved)
  const errors = problems.filter((problem) => {
    return problem.severity === 'error'
  }).length
  const status = shellStatus({
    catalog,
    languageName: language?.presentation.name ?? 'the language',
    connection,
    initialization,
    verification,
    timing,
    blockedErrors: blocked ? errors : null,
    now,
  })
  const act = (): void => {
    if (status.action === 'connect' || status.action === 'reconnect') {
      if (connection.terminated) {
        live.restartSession()
      } else {
        live.reconnect()
      }
    }
  }
  return (
    <span className={`status-message is-${status.kind}`} role="status" data-testid="status-message">
      <StatusIcon status={status} />
      <span>
        {unsaved && status.kind === 'idle' ? <UnsavedLine /> : status.text}
        {status.caseName !== undefined ? <span className="case-ref">{status.caseName}</span> : null}
        {status.after}
        {status.action !== undefined
          ? (
            <>
              {' '}
              <button type="button" className="button-link" onClick={act}>{status.actionLabel}</button>
            </>
          )
          : null}
      </span>
    </span>
  )
}

function ProblemCounts(): React.JSX.Element {
  const live = useLive()
  const problems = useStore(live.problems)
  const listed = problems.filter((problem) => {
    return problem.severity === 'error' || problem.severity === 'warning'
  })
  const errors = listed.filter((problem) => {
    return problem.severity === 'error'
  }).length
  const warnings = listed.length - errors
  return (
    <Popover.Root>
      <Popover.Trigger className={`problem-counts${listed.length > 0 ? ' has-problems' : ''}`} title="Problems in the model" aria-label={`Problems in the model: ${errors} errors, ${warnings} warnings`}>
        <Icon name="error" size="small" />
        <span>{errors}</span>
        <Icon name="alert" size="small" />
        <span>{warnings}</span>
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Positioner className="floating" side="top" align="end" sideOffset={6}>
          <Popover.Popup className="menu problems-popover">
            <Popover.Title className="menu-heading">Problems in the model</Popover.Title>
            {listed.length === 0 ? <div className="problems-empty">No problems.</div> : null}
            {listed.map((problem, index) => {
              return (
                <Popover.Close
                  key={`${problem.startLine}:${problem.startColumn}:${index}`}
                  className={`menu-item problem-item is-${problem.severity}`}
                  onClick={() => {
                    live.revealRange({
                      start: { line: problem.startLine, character: problem.startColumn },
                      end: { line: problem.endLine, character: problem.endColumn },
                    })
                  }}
                >
                  <Icon name={problem.severity === 'error' ? 'error' : 'alert'} size="small" />
                  <span className="menu-item-label">{problem.message}</span>
                  <span className="menu-item-meta">{`Ln ${problem.startLine + 1}`}</span>
                </Popover.Close>
              )
            })}
          </Popover.Popup>
        </Popover.Positioner>
      </Popover.Portal>
    </Popover.Root>
  )
}

export function StatusBar(): React.JSX.Element {
  const live = useLive()
  const shell = useShell()
  const cursor = useStore(shell.cursor)
  const readOnly = useStore(live.tabs.state, (state) => {
    return state.active !== null
  })
  const flavor = useFlavor(live)
  const language = useLanguage(live)
  const flavour = flavor && flavor.id !== flavor.languageId ? `, ${flavourText(flavor).label.toLowerCase()}` : ''
  return (
    <footer className="statusbar">
      <StatusMessage />
      <span className="status-end">
        <ProblemCounts />
        <span>{readOnly ? 'Read-only' : cursor ? `Ln ${cursor.line}, Col ${cursor.column}` : ''}</span>
        {flavor && language
          ? (
            <span className="status-language" data-language={flavor.languageId}>
              <LanguageMark languageId={flavor.languageId} />
              {`${language.presentation.name}${flavour}`}
            </span>
          )
          : null}
      </span>
    </footer>
  )
}
