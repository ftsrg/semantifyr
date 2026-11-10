/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { decodeCompressedBase64Url, encodeCompressedBase64Url } from '@semantifyr/editor-common'

import type { VerificationCaseState, VerificationState } from '../verification/engine'
import { STATUS_WORDS, type CaseRow } from './caseRows'

export const SHARED_RESULTS_PARAMETER = 'verdicts'

export type SharedVerdict =
  | 'passed'
  | 'failed'
  | 'inconclusive'
  | 'error'

export interface SharedResults {
  selectedCase: string | null
  verdicts: ReadonlyMap<string, SharedVerdict>
}

interface SharedResultsWire {
  selectedCase: string | null
  verdicts: [string, SharedVerdict][]
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
  switch (caseState.resultStatus) {
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
    default:
      return null
  }
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

export async function encodeSharedResults(results: SharedResults): Promise<string> {
  const wire: SharedResultsWire = { selectedCase: results.selectedCase, verdicts: [...results.verdicts] }
  return encodeCompressedBase64Url(JSON.stringify(wire))
}

function isVerdict(value: unknown): value is SharedVerdict {
  return VERDICTS.some((verdict) => {
    return verdict === value
  })
}

export async function decodeSharedResults(encoded: string): Promise<SharedResults | null> {
  const text = await decodeCompressedBase64Url(encoded)
  if (text === null) {
    return null
  }
  try {
    const wire = JSON.parse(text) as Partial<SharedResultsWire>
    const entries = wire.verdicts
    if (!Array.isArray(entries) || entries.length === 0) {
      return null
    }
    const verdicts = new Map<string, SharedVerdict>()
    for (const entry of entries) {
      if (!Array.isArray(entry) || typeof entry[0] !== 'string' || !isVerdict(entry[1])) {
        return null
      }
      verdicts.set(entry[0], entry[1])
    }
    return { selectedCase: typeof wire.selectedCase === 'string' ? wire.selectedCase : null, verdicts }
  } catch {
    return null
  }
}

function finished(row: CaseRow): boolean {
  return row.status !== 'not-run' && row.status !== 'running' && row.status !== 'queued'
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
      return row.status === verdict ? row : { ...row, differs: STATUS_WORDS[verdict] }
    }
    if (!running) {
      return row
    }
    return { ...row, status: verdict, word: STATUS_WORDS[verdict], message: null, time: null, hasTrace: false, shared: true }
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
