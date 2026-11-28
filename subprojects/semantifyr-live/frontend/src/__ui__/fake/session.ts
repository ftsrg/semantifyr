/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import {
  VerificationStatus,
  WitnessValidationStatus,
  type CompiledOxstsResult,
  type FlavorInfo,
  type Range,
  type VerificationCaseRequest,
  type VerificationCaseResult,
  type VerificationCaseSpecification,
  type WitnessValidationResult,
} from '@semantifyr/editor-common'
import type {
  Diagnostic,
  DidChangeTextDocumentParams,
  DidOpenTextDocumentParams,
  ExecuteCommandParams,
  InitializeResult,
  ProgressToken,
  PublishDiagnosticsParams,
  WorkDoneProgressBegin,
  WorkDoneProgressCancelParams,
  WorkDoneProgressCreateParams,
  WorkDoneProgressEnd,
} from 'vscode-languageclient'

import { SemantifyrLiveMethods, type ReadDocumentParams, type ReadDocumentResult, type VerificationsChangedParams } from '../../lib/api/lspExtensions'
import type { ActiveVerificationInfo, VerificationKind } from '../../lib/api/types'
import type { FakeBackend } from './backend'
import type { StatedCase } from './defaults'
import {
  encodeMessage,
  errorResponse,
  ErrorCodes,
  isNotification,
  isRequest,
  notification,
  parseMessage,
  request,
  response,
  type Message,
  type MessageId,
  type RequestMessage,
} from './jsonRpc'

interface DocumentRequest {
  session: FakeSession
  uri: string
  text: string
}

export interface CaseRequest {
  session: FakeSession
  uri: string
  range: Range
  portfolio: string | undefined
  // the case the session last found at that range, null for a witness
  caseId: string | null
  // aborted when the client cancels the request
  signal: AbortSignal
}

interface FeatureRequest {
  session: FakeSession
  method: string
  params: unknown
}

interface ReadRequest {
  session: FakeSession
  uri: string
}

type Answer<Request, Result> = (request: Request, stated: () => Result) => Result | Promise<Result>

// each answer a test overrides gets the one the fake gives from what is stated, to change or to pass on
export interface Answers {
  diagnostics: Answer<DocumentRequest, Diagnostic[]>
  discover: Answer<DocumentRequest, VerificationCaseSpecification[]>
  verify: Answer<CaseRequest, VerificationCaseResult>
  validate: Answer<CaseRequest, WitnessValidationResult>
  // holds a verification or validation in the server's queue until it resolves, by default not at all
  queue: Answer<CaseRequest, void>
  compiled: Answer<DocumentRequest, CompiledOxstsResult>
  read: Answer<ReadRequest, string | null>
  // a language feature of the editor, as textDocument/definition, which by default finds nothing
  feature: Answer<FeatureRequest, unknown>
}

class Unanswered extends Error {}

// so that a run is seen queued and running before its answer
const LATENCY_MS = 150

const QUIET_NOTIFICATIONS = new Set([
  'initialized',
  'exit',
  '$/setTrace',
  'workspace/didChangeConfiguration',
  'textDocument/didClose',
])

const NO_ANSWER = Symbol('no answer')

interface Active {
  requestId: MessageId
  info: ActiveVerificationInfo
  token: string
  abort: AbortController
}

interface SessionTransport {
  send: (frame: string) => void
  close: (code: number, reason: string) => void
}

function delay(milliseconds: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    const timer = setTimeout(resolve, milliseconds)
    signal?.addEventListener('abort', () => {
      clearTimeout(timer)
      resolve()
    })
  })
}

function lineRange(line: number): Range {
  return { start: { line, character: 0 }, end: { line, character: 1 } }
}

function labelOf(id: string): string {
  return id.split('::').at(-1) ?? id
}

export class FakeSession {
  readonly received: Message[] = []
  closed: { code: number, reason: string } | null = null

  private readonly backend: FakeBackend
  private readonly flavor: FlavorInfo
  private readonly url: string
  private transport: SessionTransport | null = null
  private readonly documents = new Map<string, string>()
  private readonly discovered = new Map<string, VerificationCaseSpecification[]>()
  private readonly active = new Map<MessageId, Active>()
  private nextServerId = 1
  private nextVerificationId = 1

  constructor(backend: FakeBackend, flavor: FlavorInfo, url: string) {
    this.backend = backend
    this.flavor = flavor
    this.url = url
  }

  get flavorId(): string {
    return this.flavor.id
  }

  get query(): URLSearchParams {
    return new URL(this.url).searchParams
  }

  attach(transport: SessionTransport): void {
    this.transport = transport
  }

  requests(method: string): unknown[] {
    return this.received.filter((message) => {
      return isRequest(message) && message.method === method
    }).map((message) => {
      return (message as RequestMessage).params
    })
  }

  commands(command: string): unknown[] {
    return (this.requests('workspace/executeCommand') as ExecuteCommandParams[]).filter((params) => {
      return params.command === command
    }).map((params) => {
      return params.arguments?.[0] as unknown
    })
  }

  close(code: number, reason = ''): void {
    if (this.closed !== null) {
      return
    }
    this.closed = { code, reason }
    for (const active of this.active.values()) {
      active.abort.abort()
    }
    this.active.clear()
    this.transport?.close(code, reason)
  }

  receive(frame: string | Buffer): void {
    const message = parseMessage(frame)
    this.received.push(message)
    if (isRequest(message)) {
      void this.answerRequest(message)
    } else if (isNotification(message)) {
      this.takeNotification(message.method, message.params)
    }
  }

  private send(message: Message): void {
    if (this.closed === null) {
      this.transport?.send(encodeMessage(message))
    }
  }

  private problem(problem: string): void {
    this.backend.problems.push(`${this.flavor.id}: ${problem}`)
  }

  private answer<Kind extends keyof Answers>(kind: Kind): Answers[Kind] | undefined {
    return this.backend.answers[kind]
  }

  private takeNotification(method: string, params: unknown): void {
    switch (method) {
      case 'textDocument/didOpen': {
        const { textDocument } = params as DidOpenTextDocumentParams
        this.documents.set(textDocument.uri, textDocument.text)
        void this.publishFor(textDocument.uri)
        return
      }
      case 'textDocument/didChange': {
        const { textDocument, contentChanges } = params as DidChangeTextDocumentParams
        const last = contentChanges.at(-1)
        if (last !== undefined) {
          this.documents.set(textDocument.uri, last.text)
        }
        void this.publishFor(textDocument.uri)
        return
      }
      case '$/cancelRequest': {
        this.cancel((params as { id: MessageId }).id)
        return
      }
      case 'window/workDoneProgress/cancel': {
        const { token } = params as WorkDoneProgressCancelParams
        for (const active of this.active.values()) {
          if (active.token === token) {
            active.abort.abort()
          }
        }
        return
      }
      default:
        if (!QUIET_NOTIFICATIONS.has(method)) {
          this.problem(`the fake has no answer for the notification ${method}`)
        }
    }
  }

  private documentRequest(uri: string): DocumentRequest {
    const text = this.documents.get(uri)
    if (text === undefined) {
      // the server answers a request for a document it was not sent with an error, which the client retries after
      throw new Error(`${uri} is not open in this session`)
    }
    return { session: this, uri, text }
  }

  private async publishFor(uri: string): Promise<void> {
    const documentRequest = this.documentRequest(uri)
    await delay(LATENCY_MS)
    const stated = (): Diagnostic[] => {
      return []
    }
    try {
      const diagnostics = await (this.answer('diagnostics') ?? stated)(documentRequest, stated)
      if (this.documents.get(uri) === documentRequest.text) {
        const params: PublishDiagnosticsParams = { uri, diagnostics }
        this.send(notification('textDocument/publishDiagnostics', params))
      }
    } catch (error) {
      this.problem(error instanceof Error ? error.message : String(error))
    }
  }

  private async answerRequest(message: RequestMessage): Promise<void> {
    try {
      const result = await this.resultOf(message)
      if (result !== NO_ANSWER) {
        this.send(response(message.id, result))
      }
    } catch (error) {
      const text = error instanceof Error ? error.message : String(error)
      if (error instanceof Unanswered) {
        this.problem(text)
      }
      this.send(errorResponse(message.id, { code: error instanceof Unanswered ? ErrorCodes.MethodNotFound : ErrorCodes.InternalError, message: text }))
    }
  }

  private async resultOf(message: RequestMessage): Promise<unknown> {
    const { method, params } = message
    if (method.startsWith('textDocument/')) {
      const nothing = (): null => {
        return null
      }
      return (this.answer('feature') ?? nothing)({ session: this, method, params }, nothing)
    }
    switch (method) {
      case 'initialize':
        return this.initializeResult()
      case 'shutdown':
        return null
      case SemantifyrLiveMethods.READ_DOCUMENT:
        return this.read((params as ReadDocumentParams).uri)
      case 'workspace/executeCommand':
        return this.command(message.id, params as ExecuteCommandParams)
      default:
        throw new Unanswered(`the fake has no answer for the request ${method}`)
    }
  }

  private initializeResult(): InitializeResult {
    return {
      capabilities: {
        textDocumentSync: 1,
        definitionProvider: true,
        executeCommandProvider: {
          commands: [
            this.flavor.discoveryCommand,
            this.flavor.verificationCommand,
            this.flavor.validateWitnessCommand,
            this.flavor.compiledOxstsCommand,
          ].filter((command) => {
            return typeof command === 'string'
          }),
        },
      },
    }
  }

  private async read(uri: string): Promise<ReadDocumentResult> {
    const stated = (): string | null => {
      const text = this.backend.texts[uri] ?? this.backend.cases.find((candidate) => {
        return uri in candidate.documents
      })?.documents[uri]
      if (text === undefined) {
        throw new Unanswered(`no text is stated for ${uri}`)
      }
      return text
    }
    return { text: await (this.answer('read') ?? stated)({ session: this, uri }, stated) }
  }

  private async command(id: MessageId, { command, arguments: commandArguments = [] }: ExecuteCommandParams): Promise<unknown> {
    switch (command) {
      case this.flavor.discoveryCommand:
        return this.discover(commandArguments[0] as string)
      case this.flavor.compiledOxstsCommand:
        return this.compiled(commandArguments[0] as string)
      case this.flavor.verificationCommand:
        return this.run(id, 'Verify', commandArguments[0] as VerificationCaseRequest)
      case this.flavor.validateWitnessCommand:
        return this.run(id, 'Validate', commandArguments[0] as VerificationCaseRequest)
      default:
        throw new Unanswered(`the fake has no answer for the command ${command}`)
    }
  }

  private async discover(uri: string): Promise<VerificationCaseSpecification[]> {
    const documentRequest = this.documentRequest(uri)
    const stated = (): VerificationCaseSpecification[] => {
      return this.backend.cases.map((stated) => {
        return { id: stated.id, label: labelOf(stated.id), location: { uri, range: lineRange(stated.line) } }
      })
    }
    await delay(LATENCY_MS)
    const cases = await (this.answer('discover') ?? stated)(documentRequest, stated)
    this.discovered.set(uri, cases)
    return cases
  }

  private async compiled(uri: string): Promise<CompiledOxstsResult> {
    const stated = (): CompiledOxstsResult => {
      return this.backend.compiled
    }
    return (this.answer('compiled') ?? stated)(this.documentRequest(uri), stated)
  }

  private caseAt(uri: string, range: Range): string | null {
    return this.discovered.get(uri)?.find((candidate) => {
      return candidate.location.range.start.line === range.start.line && candidate.location.range.start.character === range.start.character
    })?.id ?? null
  }

  private statedCase(caseRequest: CaseRequest): StatedCase {
    const found = this.backend.cases.find((candidate) => {
      return candidate.id === caseRequest.caseId || candidate.result.witnessUri === caseRequest.uri
    })
    if (found === undefined) {
      throw new Unanswered(`no case is stated at ${caseRequest.uri}:${caseRequest.range.start.line}`)
    }
    return found
  }

  private announce(): void {
    const params: VerificationsChangedParams = {
      active: [...this.active.values()].map((active) => {
        return active.info
      }),
    }
    this.send(notification(SemantifyrLiveMethods.VERIFICATIONS_CHANGED, params))
  }

  private progress(token: string, value: WorkDoneProgressBegin | WorkDoneProgressEnd): void {
    const params: { token: ProgressToken, value: WorkDoneProgressBegin | WorkDoneProgressEnd } = { token, value }
    this.send(notification('$/progress', params))
  }

  private cancel(id: MessageId): void {
    const active = this.active.get(id)
    if (!active) {
      return
    }
    this.active.delete(id)
    active.abort.abort()
    this.send(errorResponse(id, { code: ErrorCodes.RequestCancelled, message: `The request (id: ${id}, method: 'workspace/executeCommand') has been cancelled` }))
    this.announce()
  }

  private async run(id: MessageId, kind: VerificationKind, location: VerificationCaseRequest): Promise<unknown> {
    const abort = new AbortController()
    const portfolioId = location.portfolio ?? 'smart'
    const active: Active = {
      requestId: id,
      info: {
        verificationId: `verification-${this.nextVerificationId++}`,
        location: { uri: location.uri, range: location.range },
        portfolioId,
        kind,
        state: 'Queued',
        elapsed: 'PT0S',
      },
      token: `progress-${id}`,
      abort,
    }
    const caseRequest: CaseRequest = {
      session: this,
      uri: location.uri,
      range: location.range,
      portfolio: location.portfolio,
      caseId: this.caseAt(location.uri, location.range),
      signal: abort.signal,
    }
    this.active.set(id, active)
    this.announce()
    await this.answer('queue')?.(caseRequest, () => {})
    if (!this.active.has(id)) {
      return NO_ANSWER
    }
    active.info = { ...active.info, state: 'Running' }
    this.announce()
    const create: WorkDoneProgressCreateParams = { token: active.token }
    this.send(request(`server-${this.nextServerId++}`, 'window/workDoneProgress/create', create))
    this.progress(active.token, { kind: 'begin', title: kind === 'Verify' ? 'Verifying' : 'Validating witness', cancellable: true })
    let result: VerificationCaseResult | WitnessValidationResult
    try {
      if (kind === 'Verify') {
        const stated = (): VerificationCaseResult => {
          return this.statedCase(caseRequest).result
        }
        result = await (this.answer('verify') ?? delayed(stated))(caseRequest, stated)
      } else {
        const stated = (): WitnessValidationResult => {
          return this.statedCase(caseRequest).validation
        }
        result = await (this.answer('validate') ?? delayed(stated))(caseRequest, stated)
      }
    } finally {
      this.progress(active.token, { kind: 'end' })
    }
    if (!this.active.has(id)) {
      return NO_ANSWER
    }
    this.active.delete(id)
    this.announce()
    if (abort.signal.aborted) {
      return kind === 'Verify'
        ? { status: VerificationStatus.Cancelled, message: 'Verification cancelled', portfolioId, report: null, witnessUri: null, traceUri: null }
        : { status: WitnessValidationStatus.Cancelled, message: 'Verification cancelled', portfolioId, report: null }
    }
    return result
  }
}

function delayed<Result>(stated: () => Result): (caseRequest: CaseRequest) => Promise<Result> {
  return async (caseRequest) => {
    await delay(LATENCY_MS, caseRequest.signal)
    return stated()
  }
}
