/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { StudentRun } from './student.ts'

export interface HealthSample {
  atMs: number
  ok: boolean
  status: number | null
  latencyMs: number
  activeSessions: number | null
}

export interface Distribution {
  count: number
  median: number
  p95: number
  max: number
}

export interface Fairness {
  waitedCases: number
  servedAgainWhileOtherWaited: number
}

export interface Summary {
  connect: Distribution | null
  initialize: Distribution | null
  firstDiagnostics: Distribution | null
  discovery: Distribution | null
  editDiagnostics: Distribution | null
  verification: Distribution | null
  queued: Distribution | null
  onServer: Distribution | null
  validation: Distribution | null
  validationQueued: Distribution | null
  validationOnServer: Distribution | null
  runs: number
  failedRuns: number
  cases: number
  wrongVerdicts: number
  validations: number
  failedValidations: number
  errors: number
  timeouts: number
  errorDiagnostics: number
  healthSamples: number
  healthFailures: number
  healthLatency: Distribution | null
  maxActiveSessions: number | null
  fairness: Fairness | null
}

const clockToleranceMs = 250

interface Grant {
  session: StudentRun
  sentAtMs: number
  startedAtMs: number
}

function grantsOf(runs: StudentRun[]): Grant[] {
  return runs.flatMap((session) => {
    return session.cases.flatMap((verification): Grant[] => {
      if (verification.queueMs === null) {
        return []
      }
      return [{ session, sentAtMs: verification.sentAtMs, startedAtMs: verification.sentAtMs + verification.queueMs }]
    })
  })
}

// round robin: a student served again while another student waited the whole time between its two grants and got none in between
export function fairness(runs: StudentRun[]): Fairness | null {
  const grants = grantsOf(runs)
  const waited = grants.filter((grant) => {
    return grant.startedAtMs - grant.sentAtMs > clockToleranceMs
  })
  if (waited.length === 0) {
    return null
  }
  const servedAgain = waited.filter((grant) => {
    const previousStart = Math.max(...grants.filter((other) => {
      return other.session === grant.session && other.startedAtMs < grant.startedAtMs
    }).map((other) => {
      return other.startedAtMs
    }))
    if (!Number.isFinite(previousStart)) {
      return false
    }
    return waited.some((waiting) => {
      if (waiting.session === grant.session) {
        return false
      }
      const waitedThroughout = waiting.sentAtMs <= previousStart + clockToleranceMs && waiting.startedAtMs >= grant.startedAtMs + clockToleranceMs
      const servedBetween = grants.some((other) => {
        return other.session === waiting.session && other.startedAtMs > previousStart - clockToleranceMs && other.startedAtMs < grant.startedAtMs + clockToleranceMs
      })
      return waitedThroughout && !servedBetween
    })
  })
  return { waitedCases: waited.length, servedAgainWhileOtherWaited: servedAgain.length }
}

function percentile(sorted: number[], percent: number): number {
  return sorted[Math.max(0, Math.ceil((percent / 100) * sorted.length) - 1)] ?? Number.NaN
}

export function distribution(values: (number | null)[]): Distribution | null {
  const sorted = values.filter((value) => value !== null).sort((left, right) => left - right)
  if (sorted.length === 0) {
    return null
  }
  return {
    count: sorted.length,
    median: percentile(sorted, 50),
    p95: percentile(sorted, 95),
    max: percentile(sorted, 100),
  }
}

export function summarize(runs: StudentRun[], health: HealthSample[]): Summary {
  const cases = runs.flatMap((run) => run.cases)
  const validations = runs.flatMap((run) => run.validations)
  const activeSessions = health.map((sample) => sample.activeSessions).filter((value) => value !== null)
  return {
    connect: distribution(runs.map((run) => run.connectMs)),
    initialize: distribution(runs.map((run) => run.initializeMs)),
    firstDiagnostics: distribution(runs.map((run) => run.firstDiagnosticsMs)),
    discovery: distribution(runs.flatMap((run) => run.discoveryMs)),
    editDiagnostics: distribution(runs.map((run) => run.editDiagnosticsMs)),
    verification: distribution(cases.map((verification) => verification.wallMs)),
    queued: distribution(cases.map((verification) => verification.queueMs)),
    onServer: distribution(cases.map((verification) => verification.serverMs)),
    validation: distribution(validations.map((validation) => validation.wallMs)),
    validationQueued: distribution(validations.map((validation) => validation.queueMs)),
    validationOnServer: distribution(validations.map((validation) => validation.serverMs)),
    runs: runs.length,
    failedRuns: runs.filter((run) => run.error !== null || run.cases.some((verification) => !verification.ok)).length,
    cases: cases.length,
    wrongVerdicts: cases.filter((verification) => !verification.ok).length,
    validations: validations.length,
    failedValidations: validations.filter((validation) => validation.status === 'invalid' || validation.status === 'errored').length,
    errors: runs.filter((run) => run.error !== null).length,
    timeouts: runs.filter((run) => run.error?.timeout).length,
    errorDiagnostics: runs.reduce((total, run) => total + run.errorDiagnostics, 0),
    healthSamples: health.length,
    healthFailures: health.filter((sample) => !sample.ok).length,
    healthLatency: distribution(health.map((sample) => sample.latencyMs)),
    maxActiveSessions: activeSessions.length === 0 ? null : Math.max(...activeSessions),
    fairness: fairness(runs),
  }
}

export function failures(summary: Summary, maxVerificationP95Ms: number, aborted: boolean): string[] {
  const reasons: string[] = []
  if (aborted) {
    reasons.push('the run was stopped because /api/health failed')
  }
  if (summary.wrongVerdicts > 0) {
    reasons.push(`${summary.wrongVerdicts} verdicts were not the expected ones`)
  }
  if (summary.failedValidations > 0) {
    reasons.push(`${summary.failedValidations} witness validations came back invalid or errored`)
  }
  if (summary.errors > 0) {
    reasons.push(`${summary.errors} student runs failed with an error, ${summary.timeouts} of them on a timeout`)
  }
  if (summary.errorDiagnostics > 0) {
    reasons.push(`the models got ${summary.errorDiagnostics} error diagnostics`)
  }
  if (summary.verification && summary.verification.p95 > maxVerificationP95Ms) {
    reasons.push(`p95 verification time ${seconds(summary.verification.p95)} exceeds ${seconds(maxVerificationP95Ms)}`)
  }
  return reasons
}

function seconds(milliseconds: number): string {
  return `${(milliseconds / 1000).toFixed(2)} s`
}

export function formatSummary(summary: Summary): string {
  const rows: [string, Distribution | null][] = [
    ['connect', summary.connect],
    ['initialize', summary.initialize],
    ['first diagnostics', summary.firstDiagnostics],
    ['discovery', summary.discovery],
    ['diagnostics after edit', summary.editDiagnostics],
    ['verification', summary.verification],
    ['  queued', summary.queued],
    ['  on server', summary.onServer],
    ['witness validation', summary.validation],
    ['  queued', summary.validationQueued],
    ['  on server', summary.validationOnServer],
    ['/api/health', summary.healthLatency],
  ]
  const lines = [`${''.padEnd(24)}${'count'.padStart(7)}${'median'.padStart(11)}${'p95'.padStart(11)}${'max'.padStart(11)}`]
  for (const [label, values] of rows) {
    if (values) {
      lines.push(`${label.padEnd(24)}${String(values.count).padStart(7)}${seconds(values.median).padStart(11)}${seconds(values.p95).padStart(11)}${seconds(values.max).padStart(11)}`)
    }
  }
  lines.push(
    '',
    `student runs ${summary.runs}, failed ${summary.failedRuns}`,
    `verifications ${summary.cases}, unexpected verdicts ${summary.wrongVerdicts}`,
    `witness validations ${summary.validations}, invalid or errored ${summary.failedValidations}`,
    `errors ${summary.errors}, timeouts ${summary.timeouts}, error diagnostics ${summary.errorDiagnostics}`,
    `health samples ${summary.healthSamples}, failed ${summary.healthFailures}, max active sessions ${summary.maxActiveSessions ?? 'unknown'}`,
  )
  if (summary.fairness) {
    lines.push('', `fairness: ${summary.fairness.waitedCases} cases waited for a permit; in ${summary.fairness.servedAgainWhileOtherWaited} of them a student was served again while another waited throughout (clock tolerance ${clockToleranceMs} ms; 0 is fair)`)
  }
  if (!summary.fairness) {
    lines.push('', 'fairness: no case waited for a verification permit')
  }
  return lines.join('\n')
}

export interface BoundCase {
  model: string
  name: string
  status: string | null
  message: string | null
  wallMs: number
  serverMs: number | null
}

export interface BoundReport {
  boundMs: number
  cases: BoundCase[]
  over: BoundCase[]
  // a model whose session failed before its cases answered, with the reason
  unmeasured: { model: string, reason: string }[]
}

// a served case answers within the bound when it is verified alone; the run reports the ones that do not and fails nothing for them
export function boundOf(runs: StudentRun[], boundMs: number): BoundReport {
  const cases = runs.flatMap((run) => {
    return run.cases.map((verification) => {
      return { model: run.model, name: verification.name, status: verification.status, message: verification.message, wallMs: verification.wallMs, serverMs: verification.serverMs }
    })
  })
  return {
    boundMs,
    cases,
    over: cases.filter((verification) => {
      return verification.wallMs > boundMs
    }),
    unmeasured: runs.filter((run) => {
      return run.error !== null
    }).map((run) => {
      return { model: run.model, reason: `${run.error?.phase} failed: ${run.error?.message}` }
    }),
  }
}

export function formatBound(report: BoundReport): string {
  const lines = [`served cases verified alone: ${report.cases.length}, over the bound of ${seconds(report.boundMs)}: ${report.over.length}`]
  for (const verification of report.over) {
    lines.push(`- ${verification.model} ${verification.name}: ${seconds(verification.wallMs)}, ${verification.serverMs === null ? 'no time' : seconds(verification.serverMs)} on the server, ${verification.status ?? 'no result'}`)
  }
  for (const model of report.unmeasured) {
    lines.push(`- ${model.model} not measured: ${model.reason}`)
  }
  return lines.join('\n')
}
