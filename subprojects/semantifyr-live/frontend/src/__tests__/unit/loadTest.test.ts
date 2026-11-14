/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

// @vitest-environment node

import { afterEach, describe, expect, it, vi } from 'vitest'
import { VerificationStatus, type VerificationCaseResult } from '@semantifyr/editor-common'
import { servedExamples } from '@semantifyr/editor-common/examples'
import { servedModels } from '../../__load__/models.ts'
import { boundOf, distribution, failures, fairness, formatBound, summarize } from '../../__load__/stats.ts'
import { validateResult, type CaseRun, type RunningTracker, type StudentRun, type ValidationRun } from '../../__load__/student.ts'
import type { LspClient } from '../../lib/verification/engine.ts'
import { servedFlavors } from '../fixtures/flavors.ts'

function caseRun(overrides: Partial<CaseRun>): CaseRun {
  return {
    name: 'Case',
    edited: false,
    expected: 'passed',
    status: 'passed',
    message: null,
    ok: true,
    sentAtMs: 0,
    wallMs: 1000,
    queueMs: 10,
    serverMs: 900,
    ...overrides,
  }
}

function validationRun(overrides: Partial<ValidationRun>): ValidationRun {
  return {
    name: 'Case',
    edited: false,
    status: 'valid',
    message: null,
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
    validations: [validationRun({})],
    error: null,
    ...overrides,
  }
}

describe('servedModels', () => {
  it('reads every served example with a case, sends each as live does and expects each case\'s verdict from its manifest', () => {
    const models = servedModels()
    expect(models.map((model) => {
      return model.name
    })).toEqual(servedExamples.filter((example) => {
      return example.cases.length > 0
    }).map((example) => {
      return `${example.language}/${example.id}`
    }))
    for (const model of models) {
      expect(model.cases.length, model.name).toBeGreaterThan(0)
      expect(model.cases.every((declaredCase) => {
        return declaredCase.expected !== null
      }), model.name).toBe(true)
    }
    expect(servedModels(['sysml/level-crossing'])[0]).toMatchObject({ flavorId: 'oxsts-with-sysmlv2-library', text: expect.stringContaining('package') })
    expect(servedModels(['gamma/level-crossing'])[0]).toMatchObject({ flavorId: 'gamma' })
  })

  it('names an address the registry does not serve', () => {
    expect(() => {
      return servedModels(['oxsts/nowhere'])
    }).toThrow('the registry serves no oxsts/nowhere')
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
      studentRun({ cases: [caseRun({ status: 'timeout', ok: false, wallMs: 90_000 })], validations: [validationRun({ status: 'invalid' }), validationRun({ status: 'inconclusive' })] }),
      studentRun({ cases: [], error: { phase: 'verify', message: 'no answer after 300 s', timeout: true } }),
      studentRun({ errorDiagnostics: 2 }),
    ]
    const summary = summarize(runs, [])
    expect(summary.failedRuns).toBe(2)
    expect(failures(summary, 60_000, true)).toEqual([
      'the run was stopped because /api/health failed',
      '1 verdicts were not the expected ones',
      '1 witness validations came back invalid or errored',
      '1 student runs failed with an error, 1 of them on a timeout',
      'the models got 2 error diagnostics',
      'p95 verification time 90.00 s exceeds 60.00 s',
    ])
  })
})

describe('summarize', () => {
  it('counts the witness validations and their queue wait apart from the verifications', () => {
    const summary = summarize([
      studentRun({ validations: [validationRun({ queueMs: 30 }), validationRun({ queueMs: 50, status: 'errored' })] }),
    ], [])

    expect(summary.cases).toBe(1)
    expect(summary.validations).toBe(2)
    expect(summary.failedValidations).toBe(1)
    expect(summary.validationQueued).toEqual({ count: 2, median: 30, p95: 50, max: 50 })
  })
})

describe('fairness', () => {
  function session(student: number, starts: [number, number][]): StudentRun {
    return studentRun({
      student,
      cases: starts.map(([sentAtMs, startedAtMs]) => {
        return caseRun({ sentAtMs, queueMs: startedAtMs - sentAtMs })
      }),
    })
  }

  it('is absent when no case waited', () => {
    expect(fairness([session(0, [[0, 10], [0, 20]])])).toBeNull()
  })

  it('counts nothing when waiting students are served in turns', () => {
    const runs = [
      session(0, [[0, 0], [0, 4000], [0, 8000]]),
      session(1, [[100, 2000], [100, 6000]]),
    ]
    expect(fairness(runs)).toEqual({ waitedCases: 4, servedAgainWhileOtherWaited: 0 })
  })

  it('counts a student served again while another waited throughout', () => {
    const runs = [
      session(0, [[0, 0], [0, 4000], [0, 8000]]),
      session(1, [[100, 12000]]),
    ]
    expect(fairness(runs)).toEqual({ waitedCases: 3, servedAgainWhileOtherWaited: 2 })
  })

  it('ignores a difference within the clock tolerance', () => {
    const runs = [
      session(0, [[0, 0], [0, 4000]]),
      session(1, [[3900, 4100]]),
    ]
    expect(fairness(runs)).toEqual({ waitedCases: 1, servedAgainWhileOtherWaited: 0 })
  })
})

describe('validateResult', () => {
  const verificationCase = {
    id: 'model::Case',
    label: 'Case',
    location: {
      uri: 'file:///workspace/snippet.oxsts',
      range: { start: { line: 4, character: 0 }, end: { line: 9, character: 1 } },
    },
  }
  const witnessUri = 'file:///workspace/.artifacts/Case/witness.oxsts'

  function resultWith(witness: string | null): VerificationCaseResult {
    return {
      status: VerificationStatus.Failed,
      message: null,
      backendId: 'theta',
      portfolioId: 'smart',
      metrics: null,
      trace: { callTrace: { steps: [] } as never, witnessState: { steps: [] } as never, witnessUri: witness },
    }
  }

  function fakeClient(): LspClient & { sendRequest: ReturnType<typeof vi.fn> } {
    return {
      sendRequest: vi.fn(async () => {
        return { status: 'valid', verdict: 'failed', message: null, backendId: 'theta', portfolioId: 'smart', metrics: { totalDuration: 'PT2S' } }
      }),
      sendNotification: () => {},
    }
  }

  function runningAfter(queueMs: number): RunningTracker & { keys: string[] } {
    const keys: string[] = []
    return {
      keys,
      reset: (key) => {
        keys.push(key)
      },
      runningAt: () => {
        return 1000 + queueMs
      },
    }
  }

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('validates the witness of a result through the flavor command with the validation portfolio', async () => {
    vi.spyOn(performance, 'now').mockReturnValue(1000)
    const client = fakeClient()
    const running = runningAfter(250)

    const validation = await validateResult(client, running, {
      flavor: servedFlavors[0]!,
      verificationCase,
      result: resultWith(witnessUri),
      edited: true,
      portfolio: 'smart',
      timeoutMs: 1000,
    })

    expect(client.sendRequest).toHaveBeenCalledTimes(1)
    expect(client.sendRequest.mock.calls[0]![1]).toEqual({
      command: 'oxsts.case.validateWitness',
      arguments: [{ uri: witnessUri, range: { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } }, portfolio: 'smart' }],
    })
    expect(running.keys).toEqual([
      `Validate ${witnessUri} 0:0`,
    ])
    expect(validation).toEqual({ name: 'Case', edited: true, status: 'valid', message: null, wallMs: 0, queueMs: 250, serverMs: 2000 })
  })

  it('sends the case location for a flavor that validates by case', async () => {
    const client = fakeClient()

    await validateResult(client, runningAfter(0), {
      flavor: servedFlavors[3]!,
      verificationCase,
      result: resultWith(witnessUri),
      edited: false,
      portfolio: 'smart',
      timeoutMs: 1000,
    })

    expect(client.sendRequest.mock.calls[0]![1]).toEqual({
      command: 'gamma.witness.validate',
      arguments: [{ ...verificationCase.location, portfolio: 'smart' }],
    })
  })

  it('sends nothing for a result without a witness', async () => {
    const client = fakeClient()

    const validation = await validateResult(client, runningAfter(0), {
      flavor: servedFlavors[0]!,
      verificationCase,
      result: resultWith(null),
      edited: false,
      portfolio: 'smart',
      timeoutMs: 1000,
    })

    expect(validation).toBeNull()
    expect(client.sendRequest).not.toHaveBeenCalled()
  })
})

describe('boundOf', () => {
  it('reports the cases verified alone that took longer than the bound, and the models it could not measure', () => {
    const report = boundOf([
      studentRun({ model: 'sysml/crossroads', cases: [caseRun({ name: 'Fast', wallMs: 4000 }), caseRun({ name: 'Slow', wallMs: 31_000, serverMs: 30_500 })] }),
      studentRun({ model: 'gamma/spacecraft', cases: [], error: { phase: 'discovery', message: 'no answer', timeout: true } }),
    ], 30_000, 0)

    expect(report.over).toEqual([{ model: 'sysml/crossroads', name: 'Slow', status: 'passed', message: null, wallMs: 31_000, serverMs: 30_500 }])
    expect(report.cases).toHaveLength(2)
    expect(report.unmeasured).toEqual([{ model: 'gamma/spacecraft', reason: 'discovery failed: no answer' }])
    expect(formatBound(report).split('\n')[0]).toBe('served cases verified alone before the class, with 0 other sessions on the server: 2, over the bound of 30.00 s: 1')
  })
})
