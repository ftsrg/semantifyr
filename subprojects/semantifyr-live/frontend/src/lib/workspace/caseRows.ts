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

function statusOf(caseState: VerificationCaseState): RowStatus {
  switch (caseState.status) {
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

function messageOf(caseState: VerificationCaseState): string | null {
  switch (caseState.status) {
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
    return 'validating'
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

export function caseRowOf(caseState: VerificationCaseState): CaseRow {
  const status = statusOf(caseState)
  const finished = status !== 'not-run' && status !== 'running' && status !== 'queued'
  const duration = caseState.metrics?.totalDuration
  return {
    id: caseState.caseInfo.id,
    name: caseState.caseInfo.label.split('::').at(-1) ?? caseState.caseInfo.label,
    status,
    word: STATUS_WORDS[status],
    message: messageOf(caseState),
    time: finished && duration !== undefined ? formatSeconds(isoDurationToMs(duration)) : null,
    outdated: finished && caseState.outdated === true,
    validation: finished ? validationOf(caseState) : null,
    validationMessage: caseState.validationMessage ?? null,
    hasTrace: finished && caseState.trace !== undefined,
    waiting: status === 'running' || status === 'queued',
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
