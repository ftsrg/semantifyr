/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

// @vitest-environment node

import { describe, expect, it } from 'vitest'
import { declaredCases, tutorialStepModels } from '../__load__/models.ts'
import { distribution, failures, summarize } from '../__load__/stats.ts'
import type { CaseRun, StudentRun } from '../__load__/student.ts'

function caseRun(overrides: Partial<CaseRun>): CaseRun {
  return {
    name: 'Case',
    edited: false,
    expected: 'passed',
    status: 'passed',
    message: null,
    ok: true,
    wallMs: 1000,
    queueMs: 10,
    serverMs: 900,
    ...overrides,
  }
}

function studentRun(overrides: Partial<StudentRun>): StudentRun {
  return {
    student: 0,
    round: 0,
    model: 'model',
    connectMs: 10,
    initializeMs: 10,
    firstDiagnosticsMs: 10,
    editDiagnosticsMs: null,
    discoveryMs: [10],
    errorDiagnostics: 0,
    cases: [caseRun({})],
    error: null,
    ...overrides,
  }
}

describe('declaredCases', () => {
  it('reads the verification cases and their expected verdicts', () => {
    const source = `
class Helper {}

@VerificationCase
@Tag("expect-pass")
class Reachable {}

@Tag("expect-fail")
@VerificationCase
class Violated {}

@VerificationCase
class Untagged {}

@Tag("expect-pass")
class NotACase {}
`
    expect(declaredCases(source)).toEqual([
      { name: 'Reachable', expected: 'passed' },
      { name: 'Violated', expected: 'failed' },
      { name: 'Untagged', expected: null },
    ])
  })

  it('finds a tagged case in every tutorial step model it loads', () => {
    const models = tutorialStepModels()
    expect(models.length).toBeGreaterThan(0)
    for (const model of models) {
      expect(model.name).toMatch(/-step\d+$/)
      expect(model.cases.length).toBeGreaterThan(0)
      expect(model.cases.every((declaredCase) => declaredCase.expected !== null)).toBe(true)
    }
  })
})

describe('distribution', () => {
  it('takes nearest-rank percentiles and skips missing values', () => {
    const values = [...Array.from({ length: 20 }, (_, index) => index + 1), null]
    expect(distribution(values)).toEqual({ count: 20, median: 10, p95: 19, max: 20 })
  })

  it('is absent without values', () => {
    expect(distribution([null])).toBeNull()
  })
})

describe('failures', () => {
  it('passes a clean run', () => {
    expect(failures(summarize([studentRun({})], []), 60_000, false)).toEqual([])
  })

  it('reports wrong verdicts, errors, error diagnostics, a slow p95 and an abort', () => {
    const runs = [
      studentRun({ cases: [caseRun({ status: 'timeout', ok: false, wallMs: 90_000 })] }),
      studentRun({ cases: [], error: { phase: 'verify', message: 'no answer after 300 s', timeout: true } }),
      studentRun({ errorDiagnostics: 2 }),
    ]
    const summary = summarize(runs, [])
    expect(summary.failedRuns).toBe(2)
    expect(failures(summary, 60_000, true)).toEqual([
      'the run was stopped because /api/health failed',
      '1 verdicts were not the expected ones',
      '1 student runs failed with an error, 1 of them on a timeout',
      'the models got 2 error diagnostics',
      'p95 verification time 90.00 s exceeds 60.00 s',
    ])
  })
})
