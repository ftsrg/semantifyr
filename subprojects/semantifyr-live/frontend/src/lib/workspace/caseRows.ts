/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { isoDurationToMs } from '../util/duration'
import type { VerificationCaseState } from '../verification/engine'

export type RowStatus =
  | 'passed'
  | 'failed'
  | 'inconclusive'
  | 'error'
  | 'not-run'
  | 'running'
  | 'queued'

export type ValidationMark =
  | 'validation-queued'
  | 'validating'
  | 'validated'
  | 'validation-failed'

export interface CaseRow {
  id: string
  name: string
  status: RowStatus
  word: string
  message: string | null
  time: string | null
  outdated: boolean
  validation: ValidationMark | null
  validationMessage: string | null
  hasTrace: boolean
  // the case is queued or running, whatever verdict the row shows
  waiting: boolean
  // running or queued while the row shows the verdict of the last run, null otherwise
  rerun: 'running' | 'queued' | null
  // the verdict shown is the sharer's, until the run gives the case its own
  shared: boolean
  // the sharer's verdict, as a word, when the run's came out differently
  differs: string | null
}

export interface CaseCounts {
  passed: number
  failed: number
  inconclusive: number
  error: number
  waiting: number
  total: number
}

export const STATUS_WORDS: Readonly<Record<RowStatus, string>> = {
  'passed': 'Passed',
  'failed': 'Failed',
  'inconclusive': 'Inconclusive',
  'error': 'Error',
  'not-run': 'Not run',
  'running': 'Running',
  'queued': 'Queued',
}

const CANCELLED_MESSAGE = 'Verification cancelled'

export function formatSeconds(milliseconds: number): string {
  const seconds = milliseconds / 1000
  return seconds < 10 ? `${seconds.toFixed(1)} s` : `${Math.round(seconds)} s`
}

// a case sent to the server runs only once the server says so
function ownStatusOf(caseState: VerificationCaseState): RowStatus {
  return caseState.status === 'running' && caseState.serverState !== 'Running' ? 'queued' : statusOf(caseState.status)
}

function statusOf(status: VerificationCaseState['status']): RowStatus {
  switch (status) {
    case 'passed':
      return 'passed'
    case 'failed':
      return 'failed'
    case 'inconclusive':
    case 'timeout':
    case 'not_supported':
      return 'inconclusive'
    case 'errored':
      return 'error'
    case 'running':
      return 'running'
    case 'queued':
      return 'queued'
    case 'stale':
    case 'cancelled':
      return 'not-run'
  }
}

function messageOf(caseState: VerificationCaseState, status: VerificationCaseState['status']): string | null {
  switch (status) {
    case 'stale':
      return caseState.message === CANCELLED_MESSAGE ? 'cancelled' : null
    case 'cancelled':
      return 'cancelled'
    case 'timeout':
      return caseState.message ?? 'timed out'
    case 'not_supported':
      return caseState.message ?? 'not supported'
    case 'inconclusive':
    case 'errored':
      return caseState.message ?? null
    default:
      return null
  }
}

function validationOf(caseState: VerificationCaseState): ValidationMark | null {
  if (caseState.validating === true) {
    return caseState.validationServerState === 'Running' ? 'validating' : 'validation-queued'
  }
  switch (caseState.witnessValidation) {
    case undefined:
    case 'cancelled':
      return null
    case 'valid':
      return 'validated'
    default:
      return 'validation-failed'
  }
}

function isWaiting(status: RowStatus): status is 'running' | 'queued' {
  return status === 'running' || status === 'queued'
}

// a case run again shows the verdict it had until the new one arrives
function keptStatusOf(caseState: VerificationCaseState): VerificationCaseState['status'] | null {
  const kept = caseState.resultStatus
  if (!isWaiting(statusOf(caseState.status)) || kept === undefined || statusOf(kept) === 'not-run') {
    return null
  }
  return kept
}

function timeOf(caseState: VerificationCaseState): string | null {
  const duration = caseState.metrics?.totalDuration
  if (duration !== undefined) {
    return formatSeconds(isoDurationToMs(duration))
  }
  const ran = caseState.ranDuring
  return ran === undefined || caseState.cancelledAt !== undefined ? null : formatSeconds(ran.end - ran.start)
}

export function caseRowOf(caseState: VerificationCaseState): CaseRow {
  const kept = keptStatusOf(caseState)
  const shown = kept ?? caseState.status
  const own = ownStatusOf(caseState)
  const status = kept === null ? own : statusOf(kept)
  const finished = status !== 'not-run' && !isWaiting(status)
  return {
    id: caseState.caseInfo.id,
    name: caseState.caseInfo.label.split('::').at(-1) ?? caseState.caseInfo.label,
    status,
    word: STATUS_WORDS[status],
    message: messageOf(caseState, shown),
    time: finished ? timeOf(caseState) : null,
    outdated: finished && caseState.outdated === true,
    validation: finished ? validationOf(caseState) : null,
    validationMessage: caseState.validationMessage ?? null,
    hasTrace: finished && caseState.trace !== undefined,
    waiting: isWaiting(own),
    rerun: finished && isWaiting(own) ? own : null,
    shared: false,
    differs: null,
  }
}

export function countsOf(rows: readonly CaseRow[]): CaseCounts {
  const counts: CaseCounts = { passed: 0, failed: 0, inconclusive: 0, error: 0, waiting: 0, total: rows.length }
  for (const row of rows) {
    switch (row.status) {
      case 'passed':
      case 'failed':
      case 'inconclusive':
      case 'error':
        counts[row.status]++
        break
      case 'running':
      case 'queued':
        counts.waiting++
        break
      case 'not-run':
        break
    }
  }
  return counts
}

// failed first, as the visitor reads it: "1 failed, 1 passed"
export function countsPhrase(counts: CaseCounts): string {
  const parts = [
    [counts.failed, 'failed'],
    [counts.passed, 'passed'],
    [counts.inconclusive, 'inconclusive'],
    [counts.error, counts.error === 1 ? 'error' : 'errors'],
  ] as const
  return parts
    .filter(([count]) => {
      return count > 0
    })
    .map(([count, word]) => {
      return `${count} ${word}`
    })
    .join(', ')
}
