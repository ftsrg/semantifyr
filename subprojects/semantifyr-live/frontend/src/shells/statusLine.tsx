/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { VerdictMark } from '@semantifyr/editor-common/marks'

import { useStore } from '../lib/stores/useStore'
import { CLOSING_LINE_MS, shellStatus, type ShellStatus } from '../lib/workspace/shellStatus'
import { useCurrentVerification, useLive } from '../lib/workspace/liveContext'
import { Icon } from '../ui/icons'
import { useLanguage, useNow } from './hooks'
import { useShell } from './shellContext'

export interface StatusLine {
  status: ShellStatus
  act: () => void
}

// the status the embed's strip and the phone's bottom show, the same the desktop's status bar shows
export function useStatusLine(): StatusLine {
  const live = useLive()
  const shell = useShell()
  const catalog = useStore(live.catalog, (state) => {
    return state.status
  })
  const connection = useStore(live.connection)
  const initialization = useStore(live.initialization)
  const verification = useCurrentVerification(live)
  const timing = useStore(live.runTiming)
  const queued = useStore(live.queuedVerification)
  const blocked = useStore(shell.verifyBlocked)
  const problems = useStore(live.problems)
  const language = useLanguage(live)
  const closing = timing !== null && timing.finishedAt !== null && Date.now() - timing.finishedAt < CLOSING_LINE_MS
  const now = useNow(closing || connection.retryAt !== null ? 1000 : null)
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
    queued,
    blockedErrors: blocked ? errors : null,
    now,
  })
  return {
    status,
    act: () => {
      if (connection.terminated) {
        live.restartSession()
      } else {
        live.reconnect()
      }
    },
  }
}

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

export function StatusContent({ line }: { line: StatusLine }): React.JSX.Element {
  const { status, act } = line
  return (
    <>
      <StatusIcon status={status} />
      <span>
        {status.text}
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
    </>
  )
}
