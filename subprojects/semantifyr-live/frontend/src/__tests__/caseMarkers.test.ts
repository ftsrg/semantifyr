/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from 'vitest'
import {
  caseStatusToSeverity,
  casesToMarkers,
  defaultMessageForStatus,
} from '../lib/caseMarkers'
import type { VerificationCaseState } from '../lib/verification'

function fakeCase(
  overrides: Partial<VerificationCaseState> & { status: VerificationCaseState['status'] },
): VerificationCaseState {
  return {
    caseInfo: {
      id: 'c1',
      label: 'P1',
      location: {
        uri: 'inmemory:///workspace/Foo.oxsts',
        range: {
          start: { line: 4, character: 0 },
          end: { line: 4, character: 18 },
        },
      },
    },
    ...overrides,
  } as VerificationCaseState
}

describe('caseStatusToSeverity', () => {
  it('maps verdicts to error/warning categories', () => {
    expect(caseStatusToSeverity('failed')).toBe('error')
    expect(caseStatusToSeverity('errored')).toBe('error')
    expect(caseStatusToSeverity('inconclusive')).toBe('warning')
    expect(caseStatusToSeverity('not_supported')).toBe('warning')
  })

  it('returns null for non-terminal statuses so the editor stays clean', () => {
    expect(caseStatusToSeverity('queued')).toBeNull()
    expect(caseStatusToSeverity('running')).toBeNull()
    expect(caseStatusToSeverity('stale')).toBeNull()
    expect(caseStatusToSeverity('passed')).toBeNull()
  })
})

describe('defaultMessageForStatus', () => {
  it('mentions the case label and steers the user to the next action', () => {
    expect(defaultMessageForStatus('failed', 'P1')).toContain('P1')
    expect(defaultMessageForStatus('failed', 'P1')).toMatch(/witness/i)
    expect(defaultMessageForStatus('inconclusive', 'P1')).toMatch(/portfolio/i)
    expect(defaultMessageForStatus('not_supported', 'P1')).toMatch(/portfolio/i)
  })
})

describe('casesToMarkers', () => {
  it('emits one marker per terminal-status case and uses the supplied message', () => {
    const cases: VerificationCaseState[] = [
      fakeCase({ status: 'failed', message: 'real message' }),
      fakeCase({ status: 'passed' }),
      fakeCase({ status: 'queued' }),
    ]
    const markers = casesToMarkers(cases)
    expect(markers).toHaveLength(1)
    expect(markers[0]!.severity).toBe('error')
    expect(markers[0]!.message).toBe('real message')
    expect(markers[0]!.source).toBe('semantifyr-verify')
    expect(markers[0]!.startLine).toBe(4)
    expect(markers[0]!.endColumn).toBe(18)
  })

  it('falls back to defaultMessageForStatus when the case has no message', () => {
    const markers = casesToMarkers([fakeCase({ status: 'inconclusive' })])
    expect(markers[0]!.message).toBe(defaultMessageForStatus('inconclusive', 'P1'))
  })
})
