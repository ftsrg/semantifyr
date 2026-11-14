/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { VerdictStatus } from '@semantifyr/editor-common/marks'
import { verdictOfStatus } from '@semantifyr/editor-common/terms'

import { CASE_MESSAGES, VERDICT_WORDS } from '../../texts'
import { isoDurationToMs } from '../util/duration'
import type { VerificationCaseState } from '../verification/engine'

export type ValidationMark =
  | 'validation-queued'
  | 'validating'
  | 'validated'
  | 'validation-failed'

export interface CaseRow {
  id: string
  name: string
  status: VerdictStatus
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

const CANCELLED_MESSAGE = 'Verification cancelled'

// a wait under a second is not worth saying
export function waitedPhrase(milliseconds: number | undefined): string | null {
  return milliseconds === undefined || milliseconds < 1000 ? null : `waited ${formatSeconds(milliseconds)}`
}

export function formatSeconds(milliseconds: number): string {
  const seconds = milliseconds / 1000
  return seconds < 10 ? `${seconds.toFixed(1)} s` : `${Math.round(seconds)} s`
}

// a case sent to the server runs only once the server says so
function ownStatusOf(caseState: VerificationCaseState): VerdictStatus {
  return caseState.status === 'running' && caseState.serverState !== 'Running' ? 'queued' : statusOf(caseState.status)
}

function statusOf(status: VerificationCaseState['status']): VerdictStatus {
  switch (status) {
    case 'running':
      return 'running'
    case 'queued':
      return 'queued'
    case 'stale':
    case 'cancelled':
      return 'not-run'
    default:
      return verdictOfStatus(status) ?? 'error'
  }
}

function messageOf(caseState: VerificationCaseState, status: VerificationCaseState['status']): string | null {
  switch (status) {
    case 'stale':
      return caseState.message === CANCELLED_MESSAGE ? CASE_MESSAGES.cancelled : null
    case 'cancelled':
      return CASE_MESSAGES.cancelled
    case 'timeout':
      return caseState.message ?? CASE_MESSAGES.timeout
    case 'not_supported':
      return caseState.message ?? CASE_MESSAGES.notSupported
    case 'inconclusive':
      return caseState.message ?? CASE_MESSAGES.inconclusive
    case 'errored':
      return caseState.message ?? CASE_MESSAGES.error
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

function isWaiting(status: VerdictStatus): status is 'running' | 'queued' {
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
    word: VERDICT_WORDS[status],
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
