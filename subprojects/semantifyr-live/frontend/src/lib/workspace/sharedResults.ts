/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { verdictOfStatus } from '@semantifyr/editor-common/terms'

import type { VerificationCaseState, VerificationState } from '../verification/engine'
import { VERDICT_WORDS } from '../../texts'
import type { CaseRow } from './caseRows'

export type SharedVerdict =
  | 'passed'
  | 'failed'
  | 'inconclusive'
  | 'error'

export interface SharedResults {
  selectedCase: string | null
  verdicts: ReadonlyMap<string, SharedVerdict>
}

const VERDICTS: readonly SharedVerdict[] = [
  'passed',
  'failed',
  'inconclusive',
  'error',
]

function verdictOf(caseState: VerificationCaseState): SharedVerdict | null {
  if (caseState.outdated === true || caseState.resultStatus === undefined) {
    return null
  }
  return verdictOfStatus(caseState.resultStatus)
}

export function sharedResultsOf(state: VerificationState | undefined, selectedCase: string | null): SharedResults | null {
  const verdicts = new Map<string, SharedVerdict>()
  for (const caseState of state?.cases ?? []) {
    const verdict = verdictOf(caseState)
    if (verdict !== null) {
      verdicts.set(caseState.caseInfo.id, verdict)
    }
  }
  return verdicts.size === 0 ? null : { selectedCase: verdicts.has(selectedCase ?? '') ? selectedCase : null, verdicts }
}

function isVerdict(value: unknown): value is SharedVerdict {
  return VERDICTS.some((verdict) => {
    return verdict === value
  })
}

// null when a verdict is not one of the four or none is given
export function verdictsOf(entries: readonly (readonly [string, string])[]): ReadonlyMap<string, SharedVerdict> | null {
  const verdicts = new Map<string, SharedVerdict>()
  for (const [caseId, verdict] of entries) {
    if (!isVerdict(verdict)) {
      return null
    }
    verdicts.set(caseId, verdict)
  }
  return verdicts.size === 0 ? null : verdicts
}

function finished(row: CaseRow): boolean {
  return !row.waiting && row.status !== 'not-run' && row.status !== 'running' && row.status !== 'queued'
}

// Until the run ends a case without a result of its own shows the sharer's verdict; after it, a result that differs is flagged
export function rowsWithSharedVerdicts(rows: readonly CaseRow[], results: SharedResults | null, running: boolean): CaseRow[] {
  if (results === null) {
    return [...rows]
  }
  return rows.map((row) => {
    const verdict = results.verdicts.get(row.id)
    if (verdict === undefined) {
      return row
    }
    if (finished(row)) {
      return row.status === verdict ? row : { ...row, differs: VERDICT_WORDS[verdict] }
    }
    if (!running) {
      return row
    }
    return { ...row, status: verdict, word: VERDICT_WORDS[verdict], message: null, time: null, hasTrace: false, shared: true }
  })
}

export interface SharedComparison {
  compared: number
  differing: number
}

export function compareWithShared(rows: readonly CaseRow[], results: SharedResults): SharedComparison {
  const compared = rows.filter((row) => {
    return finished(row) && results.verdicts.has(row.id)
  }).length
  return {
    compared,
    differing: rows.filter((row) => {
      return row.differs !== null
    }).length,
  }
}
