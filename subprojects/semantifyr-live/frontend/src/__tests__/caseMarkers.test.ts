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
} from '../lib/session/caseMarkers'
import type { VerificationCaseState } from '../lib/verification'
import { sampleTrace } from './fixtures/verification-results'

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
  }
}

describe('caseStatusToSeverity', () => {
  it('maps verdicts to error/warning categories', () => {
    expect(caseStatusToSeverity('failed')).toBe('error')
    expect(caseStatusToSeverity('errored')).toBe('error')
    expect(caseStatusToSeverity('inconclusive')).toBe('warning')
    expect(caseStatusToSeverity('timeout')).toBe('warning')
    expect(caseStatusToSeverity('not_supported')).toBe('warning')
  })

  it('returns null for non-terminal statuses so the editor stays clean', () => {
    expect(caseStatusToSeverity('queued')).toBeNull()
    expect(caseStatusToSeverity('running')).toBeNull()
    expect(caseStatusToSeverity('stale')).toBeNull()
    expect(caseStatusToSeverity('passed')).toBeNull()
    expect(caseStatusToSeverity('cancelled')).toBeNull()
  })
})

describe('defaultMessageForStatus', () => {
  it('mentions the case label and steers the user to the next action', () => {
    expect(defaultMessageForStatus('failed', 'P1', true)).toContain('P1')
    expect(defaultMessageForStatus('failed', 'P1', true)).toMatch(/witness/i)
    expect(defaultMessageForStatus('inconclusive', 'P1', false)).toMatch(/portfolio/i)
    expect(defaultMessageForStatus('timeout', 'P1', false)).toMatch(/portfolio/i)
    expect(defaultMessageForStatus('not_supported', 'P1', false)).toMatch(/portfolio/i)
  })

  it('names no witness for a failed case without one', () => {
    expect(defaultMessageForStatus('failed', 'P1', false)).toContain('P1')
    expect(defaultMessageForStatus('failed', 'P1', false)).not.toMatch(/witness/i)
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
    expect(markers[0]!.message).toBe(defaultMessageForStatus('inconclusive', 'P1', false))
  })

  it('points to the witness only when the failed case carries a trace', () => {
    const markers = casesToMarkers([
      fakeCase({ status: 'failed', trace: sampleTrace }),
      fakeCase({ status: 'failed' }),
    ])
    expect(markers[0]!.message).toMatch(/witness/i)
    expect(markers[1]!.message).not.toMatch(/witness/i)
  })
})
