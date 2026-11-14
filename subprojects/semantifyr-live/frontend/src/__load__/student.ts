/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { CancellationTokenSource, createMessageConnection, type MessageConnection } from 'vscode-jsonrpc/node'
import { ConsoleLogger, toSocket, WebSocketMessageReader, WebSocketMessageWriter } from 'vscode-ws-jsonrpc'
import { parse, toSeconds } from 'iso8601-duration'
import {
  discoverVerificationCases,
  verifyCase,
  VerificationStatus,
  type FlavorInfo,
  type Location,
  type VerificationCaseResult,
  type VerificationCaseSpecification,
  type WitnessValidationStatus,
} from '@semantifyr/editor-common'
import { validateWitness, witnessValidationLocation, type LspClient } from '../lib/verification/engine.ts'
import type { LoadTestModel } from './models.ts'

export interface StudentOptions {
  webSocketBase: string
  flavor: FlavorInfo
  portfolio: string
  // null validates no witness
  validationPortfolio: string | null
  model: LoadTestModel
  edit: boolean
  // each case alone, the next sent once the last has answered, where Verify all sends every case at once
  oneAtATime: boolean
  timeoutMs: number
  signal: AbortSignal
}

export interface CaseRun {
  name: string
  edited: boolean
  expected: VerificationStatus | null
  status: VerificationStatus | null
  message: string | null
  ok: boolean
  sentAtMs: number
  wallMs: number
  queueMs: number | null
  serverMs: number | null
}

export interface ValidationRun {
  name: string
  edited: boolean
  status: WitnessValidationStatus
  message: string | null
  wallMs: number
  queueMs: number | null
  serverMs: number | null
}

export type Phase =
  | 'connect'
  | 'initialize'
  | 'diagnostics'
  | 'discovery'
  | 'verify'
  | 'edit'
  | 'validate'

export interface StudentError {
  phase: Phase
  message: string
  timeout: boolean
}

export interface StudentRun {
  student: number
  round: number
  model: string
  connectMs: number | null
  initializeMs: number | null
  firstDiagnosticsMs: number | null
  editDiagnosticsMs: number | null
  discoveryMs: number[]
  errorDiagnostics: number
  cases: CaseRun[]
  validations: ValidationRun[]
  error: StudentError | null
}

interface Diagnostic {
  severity?: number
}

class TimeoutError extends Error {}

function withTimeout<T>(promise: Promise<T>, timeoutMs: number, onTimeout?: () => void): Promise<T> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      onTimeout?.()
      reject(new TimeoutError(`no answer after ${timeoutMs / 1000} s`))
    }, timeoutMs)
    promise.then(
      (value) => {
        clearTimeout(timer)
        resolve(value)
      },
      (error: unknown) => {
        clearTimeout(timer)
        reject(error instanceof Error ? error : new Error(String(error)))
      },
    )
  })
}

function opened(webSocket: WebSocket): Promise<void> {
  return new Promise((resolve, reject) => {
    webSocket.onopen = () => {
      resolve()
    }
    webSocket.onerror = () => {
      reject(new Error('the WebSocket did not open'))
    }
  })
}

function caseName(verificationCase: VerificationCaseSpecification): string {
  return verificationCase.id.split('::').at(-1) ?? verificationCase.id
}

function milliseconds(duration: string | undefined): number | null {
  return duration ? toSeconds(parse(duration)) * 1000 : null
}

function diagnosticsListener(connection: MessageConnection, uri: string) {
  let waiting: ((diagnostics: Diagnostic[]) => void) | null = null
  connection.onNotification('textDocument/publishDiagnostics', (params: { uri: string, diagnostics: Diagnostic[] }) => {
    if (params.uri === uri) {
      waiting?.(params.diagnostics)
      waiting = null
    }
  })
  return () => new Promise<Diagnostic[]>((resolve) => {
    waiting = resolve
  })
}

export interface RunningTracker {
  reset: (key: string) => void
  runningAt: (key: string) => number | null
}

interface ActiveVerification {
  kind: string
  state: string
  location?: Location
}

function activityKey(kind: 'Verify' | 'Validate', location: Location): string {
  return `${kind} ${location.uri} ${location.range.start.line}:${location.range.start.character}`
}

// the server reports Queued, then Running once a verification permit is free; validations run beside the verifications
function runningListener(connection: MessageConnection): RunningTracker {
  const runningAt = new Map<string, number>()
  connection.onNotification('semantifyr/live/session/verification/changed', (params: { active?: ActiveVerification[] }) => {
    for (const entry of params.active ?? []) {
      if (entry.state === 'Running' && entry.location && (entry.kind === 'Verify' || entry.kind === 'Validate')) {
        const key = activityKey(entry.kind, entry.location)
        if (!runningAt.has(key)) {
          runningAt.set(key, performance.now())
        }
      }
    }
  })
  return {
    reset(key) {
      runningAt.delete(key)
    },
    runningAt(key) {
      return runningAt.get(key) ?? null
    },
  }
}

export interface ValidationRequest {
  flavor: FlavorInfo
  verificationCase: VerificationCaseSpecification
  result: VerificationCaseResult | null
  edited: boolean
  portfolio: string
  timeoutMs: number
}

// as the editor's auto-validation: every result with a witness, sent by case where the flavor validates by case
export async function validateResult(client: LspClient, running: RunningTracker, request: ValidationRequest): Promise<ValidationRun | null> {
  const witnessUri = request.result?.trace?.witnessUri
  const command = request.flavor.validateWitnessCommand
  if (!witnessUri || !command) {
    return null
  }
  const location = witnessValidationLocation(request.verificationCase.location, witnessUri, request.flavor.validateWitnessByCase)
  const key = activityKey('Validate', location)
  const tokenSource = new CancellationTokenSource()
  running.reset(key)
  const sentAt = performance.now()
  const outcome = await withTimeout(validateWitness(client, command, location, { portfolioId: request.portfolio }, tokenSource.token), request.timeoutMs, () => {
    tokenSource.cancel()
  })
  const runningAt = running.runningAt(key)
  return {
    name: caseName(request.verificationCase),
    edited: request.edited,
    status: outcome.status,
    message: outcome.message ?? null,
    wallMs: performance.now() - sentAt,
    queueMs: runningAt === null ? null : runningAt - sentAt,
    serverMs: milliseconds(outcome.metrics?.totalDuration),
  }
}

export async function runStudent(student: number, round: number, options: StudentOptions): Promise<StudentRun> {
  const { flavor, model, timeoutMs, signal } = options
  const run: StudentRun = {
    student,
    round,
    model: model.name,
    connectMs: null,
    initializeMs: null,
    firstDiagnosticsMs: null,
    editDiagnosticsMs: null,
    discoveryMs: [],
    errorDiagnostics: 0,
    cases: [],
    validations: [],
    error: null,
  }
  const uri = `file:///workspace/${flavor.fileName}`
  const state: { phase: Phase, closeReason: string | null } = { phase: 'connect', closeReason: null }
  const webSocket = new WebSocket(`${options.webSocketBase}/ws/lsp/${encodeURIComponent(flavor.id)}`)
  webSocket.addEventListener('close', (event) => {
    state.closeReason = `${event.code} ${event.reason}`.trim()
  })
  const abort = () => {
    webSocket.close()
  }
  signal.addEventListener('abort', abort)

  const timed = async <T>(promise: Promise<T>, onTimeout?: () => void) => {
    const startedAt = performance.now()
    const value = await withTimeout(promise, timeoutMs, onTimeout)
    return { value, elapsedMs: performance.now() - startedAt }
  }

  try {
    run.connectMs = (await timed(opened(webSocket))).elapsedMs
    const socket = toSocket(webSocket)
    const connection = createMessageConnection(new WebSocketMessageReader(socket), new WebSocketMessageWriter(socket), new ConsoleLogger())
    connection.onClose(() => {
      connection.dispose()
    })
    connection.onRequest(() => null)
    const nextDiagnostics = diagnosticsListener(connection, uri)
    const running = runningListener(connection)
    connection.listen()

    state.phase = 'initialize'
    run.initializeMs = (await timed(connection.sendRequest('initialize', {
      processId: null,
      rootUri: 'file:///workspace/',
      workspaceFolders: [{ uri: 'file:///workspace/', name: 'workspace' }],
      capabilities: {
        workspace: { workspaceFolders: true, executeCommand: {} },
        window: { workDoneProgress: true },
      },
    }))).elapsedMs
    await connection.sendNotification('initialized', {})

    state.phase = 'diagnostics'
    const firstDiagnostics = nextDiagnostics()
    await connection.sendNotification('textDocument/didOpen', {
      textDocument: { uri, languageId: flavor.languageId, version: 1, text: model.text },
    })
    const diagnostics = await timed(firstDiagnostics)
    run.firstDiagnosticsMs = diagnostics.elapsedMs
    run.errorDiagnostics = diagnostics.value.filter((diagnostic) => diagnostic.severity === 1).length

    const discover = async () => {
      state.phase = 'discovery'
      const cases = await timed(discoverVerificationCases(connection, flavor.discoveryCommand, uri))
      run.discoveryMs.push(cases.elapsedMs)
      const discovered = cases.value.map(caseName).sort().join(', ')
      const declared = model.cases.map((declaredCase) => declaredCase.name).sort().join(', ')
      if (discovered !== declared) {
        throw new Error(`discovered [${discovered}], the model declares [${declared}]`)
      }
      return cases.value
    }

    const validations: Promise<void>[] = []
    const validationFailures: Error[] = []
    const validate = (request: ValidationRequest) => {
      validations.push(validateResult(connection, running, request).then(
        (validation) => {
          if (validation) {
            run.validations.push(validation)
          }
        },
        (error: unknown) => {
          validationFailures.push(error instanceof Error ? error : new Error(String(error)))
        },
      ))
    }

    const verifyAll = async (cases: VerificationCaseSpecification[], edited: boolean) => {
      state.phase = 'verify'
      const verifyOne = async (verificationCase: VerificationCaseSpecification): Promise<CaseRun> => {
        const name = caseName(verificationCase)
        const expected = model.cases.find((declaredCase) => declaredCase.name === name)?.expected ?? null
        const location = { uri, range: verificationCase.location.range }
        const key = activityKey('Verify', location)
        const tokenSource = new CancellationTokenSource()
        running.reset(key)
        const sentAt = performance.now()
        const result = await timed(
          verifyCase(connection, flavor.verificationCommand, location, {
            portfolio: options.portfolio,
            token: tokenSource.token,
          }),
          () => {
            tokenSource.cancel()
          },
        )
        const runningAt = running.runningAt(key)
        const status = result.value?.status ?? null
        if (options.validationPortfolio !== null) {
          validate({ flavor, verificationCase, result: result.value, edited, portfolio: options.validationPortfolio, timeoutMs })
        }
        return {
          name,
          edited,
          expected,
          status,
          message: result.value?.message ?? null,
          ok: expected ? status === expected : status === VerificationStatus.Passed || status === VerificationStatus.Failed,
          sentAtMs: sentAt,
          wallMs: result.elapsedMs,
          queueMs: runningAt === null ? null : runningAt - sentAt,
          serverMs: milliseconds(result.value?.metrics?.totalDuration),
        }
      }
      if (options.oneAtATime) {
        for (const verificationCase of cases) {
          run.cases.push(await verifyOne(verificationCase))
        }
        return
      }
      run.cases.push(...await Promise.all(cases.map(verifyOne)))
    }

    await verifyAll(await discover(), false)

    if (options.edit) {
      state.phase = 'edit'
      const editDiagnostics = nextDiagnostics()
      await connection.sendNotification('textDocument/didChange', {
        textDocument: { uri, version: 2 },
        contentChanges: [{ text: `${model.text}\n// edited\n` }],
      })
      run.editDiagnosticsMs = (await timed(editDiagnostics)).elapsedMs
      await verifyAll(await discover(), true)
    }

    state.phase = 'validate'
    await Promise.all(validations)
    const [validationFailure] = validationFailures
    if (validationFailure) {
      throw validationFailure
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    run.error = {
      phase: state.phase,
      message: signal.aborted ? `aborted: ${message}` : state.closeReason ? `${message} (socket closed: ${state.closeReason})` : message,
      timeout: error instanceof TimeoutError,
    }
  } finally {
    signal.removeEventListener('abort', abort)
    webSocket.close()
  }
  return run
}
