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
  type VerificationCaseSpecification,
} from '@semantifyr/editor-common'
import type { LoadTestModel } from './models.ts'

export interface StudentOptions {
  webSocketBase: string
  flavor: FlavorInfo
  portfolio: string
  model: LoadTestModel
  edit: boolean
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
  wallMs: number
  queueMs: number | null
  serverMs: number | null
}

export type Phase = 'connect' | 'initialize' | 'diagnostics' | 'discovery' | 'verify' | 'edit'

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

// the server reports Queued, then Running once a verification permit is free; a student runs one verification at a time
function runningListener(connection: MessageConnection) {
  let runningAt: number | null = null
  connection.onNotification('semantifyr/live/session/verification/changed', (params: { active?: { state: string }[] }) => {
    if (runningAt === null && params.active?.some((entry) => entry.state === 'Running')) {
      runningAt = performance.now()
    }
  })
  return {
    reset() {
      runningAt = null
    },
    runningAt() {
      return runningAt
    },
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

    const verifyAll = async (cases: VerificationCaseSpecification[], edited: boolean) => {
      state.phase = 'verify'
      for (const verificationCase of cases) {
        const name = caseName(verificationCase)
        const expected = model.cases.find((declaredCase) => declaredCase.name === name)?.expected ?? null
        const tokenSource = new CancellationTokenSource()
        running.reset()
        const sentAt = performance.now()
        const result = await timed(
          verifyCase(connection, flavor.verificationCommand, { uri, range: verificationCase.location.range }, {
            portfolio: options.portfolio,
            token: tokenSource.token,
          }),
          () => {
            tokenSource.cancel()
          },
        )
        const runningAt = running.runningAt()
        const status = result.value?.status ?? null
        run.cases.push({
          name,
          edited,
          expected,
          status,
          message: result.value?.message ?? null,
          ok: expected ? status === expected : status === VerificationStatus.Passed || status === VerificationStatus.Failed,
          wallMs: result.elapsedMs,
          queueMs: runningAt === null ? null : runningAt - sentAt,
          serverMs: milliseconds(result.value?.metrics?.totalDuration),
        })
      }
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
