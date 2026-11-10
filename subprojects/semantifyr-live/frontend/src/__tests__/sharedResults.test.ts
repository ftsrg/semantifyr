/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from 'vitest'

import type { VerificationCaseState, VerificationState } from '../lib/verification/engine'
import { caseRowOf } from '../lib/workspace/caseRows'
import {
  compareWithShared,
  decodeSharedResults,
  encodeSharedResults,
  rowsWithSharedVerdicts,
  sharedResultsOf,
  type SharedResults,
} from '../lib/workspace/sharedResults'
import { sampleCase } from './fixtures/cases'

// Goal: the results part of a link carries the case verdicts and nothing else, and a receiver sees the sharer's verdicts
// only until its own run gives a case a result, flagging the results that differ

function finishedCase(id: string, status: VerificationCaseState['status']): VerificationCaseState {
  return { caseInfo: sampleCase(id), status, resultStatus: status }
}

function waitingCase(id: string, status: 'queued' | 'running'): VerificationCaseState {
  return { caseInfo: sampleCase(id), status }
}

const SHARED: SharedResults = {
  selectedCase: 'a',
  verdicts: new Map([
    ['a', 'failed'],
    ['b', 'passed'],
  ]),
}

describe('the results part of a share link', () => {
  it('round-trips the selected case and each verdict', async () => {
    const decoded = await decodeSharedResults(await encodeSharedResults(SHARED))

    expect(decoded).toEqual(SHARED)
  })

  it('reads a link that carries no case selected', async () => {
    const decoded = await decodeSharedResults(await encodeSharedResults({ selectedCase: null, verdicts: new Map([['a', 'error']]) }))

    expect(decoded?.selectedCase).toBeNull()
    expect(decoded?.verdicts.get('a')).toBe('error')
  })

  it('does not read what is not a results part', async () => {
    expect(await decodeSharedResults('not-a-link')).toBeNull()
  })

  it('carries a verdict per finished case, the outdated and the unfinished left out', () => {
    const state: VerificationState = {
      phase: 'done',
      discovery: 'discovered',
      cases: [
        finishedCase('a', 'failed'),
        finishedCase('b', 'timeout'),
        { ...finishedCase('c', 'passed'), outdated: true },
        { caseInfo: sampleCase('d'), status: 'stale' },
      ],
    }

    const results = sharedResultsOf(state, 'a')

    expect([...results!.verdicts]).toEqual([
      ['a', 'failed'],
      ['b', 'inconclusive'],
    ])
    expect(results?.selectedCase).toBe('a')
  })

  it('carries no results when no case has a result', () => {
    const state: VerificationState = { phase: 'idle', discovery: 'discovered', cases: [{ caseInfo: sampleCase('a'), status: 'stale' }] }

    expect(sharedResultsOf(state, null)).toBeNull()
  })
})

describe('the sharer\'s verdicts on a receiver\'s rows', () => {
  it('shows the sharer\'s verdict on a case still waiting for its result, marked as shared', () => {
    const rows = [waitingCase('a', 'running'), waitingCase('b', 'queued')].map(caseRowOf)

    const shown = rowsWithSharedVerdicts(rows, SHARED, true)

    expect(shown.map((row) => {
      return [row.status, row.shared, row.waiting]
    })).toEqual([
      ['failed', true, true],
      ['passed', true, true],
    ])
  })

  it('shows the receiver\'s own state once the run ends', () => {
    const rows = [{ caseInfo: sampleCase('a'), status: 'stale' as const }].map(caseRowOf)

    const shown = rowsWithSharedVerdicts(rows, SHARED, false)

    expect(shown[0]).toMatchObject({ status: 'not-run', shared: false, differs: null })
  })

  it('replaces the sharer\'s verdict with the case\'s own result, and flags it where it differs', () => {
    const rows = [finishedCase('a', 'failed'), finishedCase('b', 'inconclusive')].map(caseRowOf)

    const shown = rowsWithSharedVerdicts(rows, SHARED, true)

    expect(shown[0]).toMatchObject({ status: 'failed', shared: false, differs: null })
    expect(shown[1]).toMatchObject({ status: 'inconclusive', shared: false, differs: 'Passed' })
    expect(compareWithShared(shown, SHARED)).toEqual({ compared: 2, differing: 1 })
  })

  it('leaves a case the link does not name alone', () => {
    const rows = [waitingCase('other', 'running')].map(caseRowOf)

    expect(rowsWithSharedVerdicts(rows, SHARED, true)[0]).toMatchObject({ status: 'running', shared: false })
  })

  it('changes nothing without a shared link', () => {
    const rows = [finishedCase('a', 'passed')].map(caseRowOf)

    expect(rowsWithSharedVerdicts(rows, null, true)).toEqual(rows)
  })
})
