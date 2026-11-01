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

export interface Summary {
  connect: Distribution | null
  initialize: Distribution | null
  firstDiagnostics: Distribution | null
  discovery: Distribution | null
  editDiagnostics: Distribution | null
  verification: Distribution | null
  queued: Distribution | null
  onServer: Distribution | null
  runs: number
  failedRuns: number
  cases: number
  wrongVerdicts: number
  errors: number
  timeouts: number
  errorDiagnostics: number
  healthSamples: number
  healthFailures: number
  healthLatency: Distribution | null
  maxActiveSessions: number | null
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
    runs: runs.length,
    failedRuns: runs.filter((run) => run.error !== null || run.cases.some((verification) => !verification.ok)).length,
    cases: cases.length,
    wrongVerdicts: cases.filter((verification) => !verification.ok).length,
    errors: runs.filter((run) => run.error !== null).length,
    timeouts: runs.filter((run) => run.error?.timeout).length,
    errorDiagnostics: runs.reduce((total, run) => total + run.errorDiagnostics, 0),
    healthSamples: health.length,
    healthFailures: health.filter((sample) => !sample.ok).length,
    healthLatency: distribution(health.map((sample) => sample.latencyMs)),
    maxActiveSessions: activeSessions.length === 0 ? null : Math.max(...activeSessions),
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

function seconds(ms: number): string {
  return `${(ms / 1000).toFixed(2)} s`
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
    `errors ${summary.errors}, timeouts ${summary.timeouts}, error diagnostics ${summary.errorDiagnostics}`,
    `health samples ${summary.healthSamples}, failed ${summary.healthFailures}, max active sessions ${summary.maxActiveSessions ?? 'unknown'}`,
  )
  return lines.join('\n')
}
