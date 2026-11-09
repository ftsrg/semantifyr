/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { setTimeout as sleep } from 'node:timers/promises'
import { parseArgs } from 'node:util'
import type { FlavorsResponse, PortfoliosResponse } from '@semantifyr/editor-common'
import type { InfoResponse } from '../lib/api/types.ts'
import { normalizeBaseUrl } from '../lib/api/urls.ts'
import { readModel, tutorialStepModels } from './models.ts'
import { failures, formatSummary, summarize, type HealthSample } from './stats.ts'
import { runStudent, type StudentRun } from './student.ts'

const usage = `Usage: npm run load-test -- [options]

Simulates a class on a live server: each student opens a tutorial model, waits for diagnostics, discovers and verifies every case with the default portfolio, edits the model, verifies again and disconnects.

  --url <url>               live server (default $SEMANTIFYR_LIVE_URL, else https://live-staging.semantifyr.org)
  --students <n>            concurrent students (default 30)
  --ramp <seconds>          students join evenly over this time (default 60)
  --rounds <n>              sessions per student, each on the next model (default 1)
  --models <files>          comma-separated .oxsts files (default the tutorial step models with a case)
  --portfolio <id>          (default the server's default portfolio)
  --no-edit                 skip the edit and the second verification
  --timeout <seconds>       per request, verification queueing included (default 300)
  --max-verify-p95 <s>      fail when the p95 verification time exceeds this (default 60)
  --output <file>           JSON result (default build/load-test/<start time>.json)`

// mirrors the container health check: a 5 s timeout, unhealthy after 3 failures in a row
const healthIntervalMs = 5000
const healthTimeoutMs = 5000
const healthFailureLimit = 3

function positive(name: string, value: string, integer: boolean): number {
  const number = Number(value)
  if (!Number.isFinite(number) || number < 0 || (integer && (!Number.isInteger(number) || number === 0))) {
    throw new Error(`--${name} must be a ${integer ? 'positive integer' : 'non-negative number'}, got ${value}`)
  }
  return number
}

async function getJson<T>(url: string): Promise<T> {
  const response = await fetch(url, { signal: AbortSignal.timeout(healthTimeoutMs) }).catch((error: unknown) => {
    throw new Error(`${url} did not answer: ${error instanceof Error ? error.message : String(error)}`)
  })
  if (!response.ok) {
    throw new Error(`${url} answered HTTP ${response.status}`)
  }
  return (await response.json()) as T
}

async function activeSessions(httpBase: string): Promise<number | null> {
  try {
    return (await getJson<InfoResponse>(`${httpBase}/api/info`)).activeSessions
  } catch {
    return null
  }
}

async function sampleHealth(httpBase: string, origin: number): Promise<HealthSample> {
  const at = performance.now()
  const sessions = activeSessions(httpBase)
  const status = await fetch(`${httpBase}/api/health`, { signal: AbortSignal.timeout(healthTimeoutMs) }).then(
    (response) => response.status,
    () => null,
  )
  return {
    atMs: at - origin,
    ok: status === 200,
    status,
    latencyMs: performance.now() - at,
    activeSessions: await sessions,
  }
}

async function main(): Promise<number> {
  const { values } = parseArgs({
    allowNegative: true,
    options: {
      'url': { type: 'string', default: process.env.SEMANTIFYR_LIVE_URL ?? 'https://live-staging.semantifyr.org' },
      'students': { type: 'string', default: '30' },
      'ramp': { type: 'string', default: '60' },
      'rounds': { type: 'string', default: '1' },
      'models': { type: 'string' },
      'portfolio': { type: 'string' },
      'edit': { type: 'boolean', default: true },
      'timeout': { type: 'string', default: '300' },
      'max-verify-p95': { type: 'string', default: '60' },
      'output': { type: 'string' },
      'help': { type: 'boolean', default: false },
    },
  })
  if (values.help) {
    console.log(usage)
    return 0
  }
  const students = positive('students', values.students, true)
  const rampMs = positive('ramp', values.ramp, false) * 1000
  const rounds = positive('rounds', values.rounds, true)
  const timeoutMs = positive('timeout', values.timeout, false) * 1000
  const maxVerificationP95Ms = positive('max-verify-p95', values['max-verify-p95'], false) * 1000
  const models = values.models ? values.models.split(',').map((path) => readModel(resolve(path.trim()))) : tutorialStepModels()
  const caseless = models.filter((model) => model.cases.length === 0)
  if (models.length === 0 || caseless.length > 0) {
    throw new Error(`every model must declare a verification case: ${caseless.map((model) => model.name).join(', ') || 'no models'}`)
  }
  const startedAt = new Date()
  const output = resolve(values.output ?? `build/load-test/${startedAt.toISOString().replace(/[:.]/g, '-')}.json`)

  const { http: httpBase, ws: webSocketBase } = normalizeBaseUrl(values.url)
  const flavor = (await getJson<FlavorsResponse>(`${httpBase}/api/flavors`)).flavors.find((candidate) => candidate.id === 'oxsts')
  if (!flavor) {
    throw new Error(`${httpBase} serves no oxsts flavor`)
  }
  const portfolio = values.portfolio ?? (await getJson<Partial<PortfoliosResponse>>(`${httpBase}/api/portfolios`)).defaultPortfolio
  if (!portfolio) {
    throw new Error(`${httpBase} names no default portfolio, pass --portfolio`)
  }
  const info = await getJson<InfoResponse>(`${httpBase}/api/info`).catch(() => null)

  console.log(`Load test against ${httpBase}${info ? ` (version ${info.version}, commit ${info.commit}, ${info.activeSessions} active sessions)` : ''}`)
  console.log(`students ${students}, rounds ${rounds}, ramp ${rampMs / 1000} s, edit ${values.edit ? 'on' : 'off'}, portfolio ${portfolio}, timeout ${timeoutMs / 1000} s`)
  console.log(`models: ${models.map((model) => `${model.name} (${model.cases.length})`).join(', ')}`)

  const origin = performance.now()
  const elapsed = () => `${((performance.now() - origin) / 1000).toFixed(1).padStart(6)} s`
  const abort = new AbortController()
  const health: HealthSample[] = []
  let consecutiveHealthFailures = 0
  const sampler = setInterval(() => {
    void sampleHealth(httpBase, origin).then((sample) => {
      health.push(sample)
      consecutiveHealthFailures = sample.ok ? 0 : consecutiveHealthFailures + 1
      if (!sample.ok) {
        console.log(`${elapsed()}  /api/health failed (${sample.status ?? 'no answer'}), ${consecutiveHealthFailures} in a row`)
      }
      if (consecutiveHealthFailures >= healthFailureLimit && !abort.signal.aborted) {
        console.log(`${elapsed()}  stopping the run`)
        abort.abort()
      }
    })
  }, healthIntervalMs)

  const runs = (await Promise.all(Array.from({ length: students }, async (_, student) => {
    const results: StudentRun[] = []
    try {
      await sleep((student * rampMs) / students, undefined, { signal: abort.signal })
    } catch {
      return results
    }
    for (let round = 0; round < rounds && !abort.signal.aborted; round++) {
      const model = models[(student + round) % models.length]
      if (!model) {
        break
      }
      const run = await runStudent(student, round, { webSocketBase, flavor, portfolio, model, edit: values.edit, timeoutMs, signal: abort.signal })
      const wrong = run.cases.filter((verification) => !verification.ok).map((verification) => `${verification.name} ${verification.status ?? 'no result'}${verification.message ? ` (${verification.message})` : ''}`)
      const outcome = run.error ? `${run.error.phase} failed: ${run.error.message}` : `${run.cases.length} verifications${wrong.length > 0 ? `, unexpected: ${wrong.join('; ')}` : ''}`
      console.log(`${elapsed()}  student ${student + 1} round ${round + 1} ${model.name}: ${outcome}`)
      results.push(run)
    }
    return results
  }))).flat()

  clearInterval(sampler)
  await sleep(2000)
  health.push(await sampleHealth(httpBase, origin))
  const durationMs = performance.now() - origin

  const summary = summarize(runs, health)
  const reasons = failures(summary, maxVerificationP95Ms, abort.signal.aborted)
  console.log(`\nfinished in ${(durationMs / 1000).toFixed(1)} s, ${health.at(-1)?.activeSessions ?? 'unknown'} active sessions afterwards\n`)
  console.log(formatSummary(summary))
  console.log(reasons.length === 0 ? '\nPASSED' : `\nFAILED\n${reasons.map((reason) => `- ${reason}`).join('\n')}`)

  mkdirSync(dirname(output), { recursive: true })
  writeFileSync(output, JSON.stringify({
    url: httpBase,
    server: info,
    startedAt: startedAt.toISOString(),
    durationMs,
    options: { students, rampMs, rounds, edit: values.edit, timeoutMs, maxVerificationP95Ms, portfolio, models: models.map((model) => model.name) },
    summary,
    failures: reasons,
    health,
    runs,
  }, null, 2))
  console.log(`result: ${output}`)
  return reasons.length === 0 ? 0 : 1
}

main().then(
  (code) => {
    process.exit(code)
  },
  (error: unknown) => {
    console.error(error instanceof Error ? error.message : error)
    process.exit(2)
  },
)
