/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type {
  CompiledOxstsResult,
  Range,
  VerificationCaseResult,
  VerificationCaseSpecification,
  WitnessValidationResult,
} from '@semantifyr/editor-common'

import { flavorFixture, textKey, type Diagnostic, type FlavorFixture, type ProgressValue, type RecordedDocument } from './fixtures'
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

export interface DocumentRequest {
  session: MockSession
  uri: string
  text: string
}

export interface CaseRequest {
  session: MockSession
  command: string
  uri: string
  range: Range
  portfolio: string | undefined
  // the case of the last discovered list at that range, null for a witness
  caseId: string | null
  // aborted when the client cancels the request
  signal: AbortSignal
}

export interface FeatureRequest {
  session: MockSession
  method: string
  params: unknown
}

export interface ReadRequest {
  session: MockSession
  uri: string
}

export type Answer<Request, Result> = (request: Request, recorded: () => Result) => Result | Promise<Result>

// what a test overrides of the default session; each answer gets the recorded one to change or to pass on
export interface Answers {
  diagnostics: Answer<DocumentRequest, Diagnostic[]>
  discover: Answer<DocumentRequest, VerificationCaseSpecification[]>
  verify: Answer<CaseRequest, VerificationCaseResult>
  validate: Answer<CaseRequest, WitnessValidationResult>
  compiled: Answer<DocumentRequest, CompiledOxstsResult>
  read: Answer<ReadRequest, string | null>
  // a language feature of the editor, as textDocument/definition, which by default finds nothing
  feature: Answer<FeatureRequest, unknown>
}

export class Unanswered extends Error {}

// the server's language features, which no UI test asserts: they answer as a server with nothing to offer
const EDITOR_FEATURES = new Set([
  'textDocument/hover',
  'textDocument/completion',
  'textDocument/signatureHelp',
  'textDocument/definition',
  'textDocument/references',
  'textDocument/documentHighlight',
  'textDocument/documentSymbol',
  'textDocument/codeAction',
  'textDocument/codeLens',
  'textDocument/formatting',
  'textDocument/rangeFormatting',
  'textDocument/prepareRename',
  'textDocument/foldingRange',
  'textDocument/semanticTokens/full',
  'textDocument/inlayHint',
  'textDocument/prepareTypeHierarchy',
  'textDocument/prepareCallHierarchy',
  'workspace/symbol',
])

const QUIET_NOTIFICATIONS = new Set([
  'initialized',
  'exit',
  '$/setTrace',
  'workspace/didChangeConfiguration',
  'workspace/didChangeWatchedFiles',
  'textDocument/didClose',
  'textDocument/didSave',
])

const CHANGED = 'semantifyr/live/session/verification/changed'

interface Active {
  requestId: MessageId
  verificationId: string
  kind: 'Verify' | 'Validate'
  location: { uri: string, range: Range }
  portfolioId: string
  state: 'Queued' | 'Running'
  token: string
  abort: AbortController
}

export interface SessionTransport {
  send: (frame: string) => void
  close: (code: number, reason: string) => void
}

export interface SessionOptions {
  flavorId: string
  url: string
  answers: () => Partial<Answers>
  // how long the default answers take, so a run is seen running
  delayMs: () => number
  problem: (problem: string) => void
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

function sameRange(left: Range, right: Range): boolean {
  return left.start.line === right.start.line && left.start.character === right.start.character
}

export class MockSession {
  readonly flavorId: string
  readonly url: string
  readonly received: Message[] = []
  readonly documents = new Map<string, string>()
  closed: { code: number, reason: string } | null = null

  private readonly options: SessionOptions
  private readonly fixture: FlavorFixture
  private transport: SessionTransport | null = null
  private readonly discovered = new Map<string, VerificationCaseSpecification[]>()
  private readonly lastDiagnostics = new Map<string, Diagnostic[]>()
  private readonly lastCompiled = new Map<string, CompiledOxstsResult>()
  private readonly active = new Map<MessageId, Active>()
  private nextServerId = 1
  private nextVerificationId = 1

  constructor(options: SessionOptions) {
    this.options = options
    this.flavorId = options.flavorId
    this.url = options.url
    this.fixture = flavorFixture(options.flavorId)
  }

  attach(transport: SessionTransport): void {
    this.transport = transport
  }

  get query(): URLSearchParams {
    return new URL(this.url).searchParams
  }

  requests(method: string): unknown[] {
    return this.received.filter((message) => {
      return isRequest(message) && message.method === method
    }).map((message) => {
      return (message as RequestMessage).params
    })
  }

  commands(command: string): unknown[] {
    return this.requests('workspace/executeCommand').filter((params) => {
      return (params as { command: string }).command === command
    }).map((params) => {
      return (params as { arguments: unknown[] }).arguments[0]
    })
  }

  send(message: Message): void {
    if (this.closed === null) {
      this.transport?.send(encodeMessage(message))
    }
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

  publishDiagnostics(uri: string, diagnostics: Diagnostic[]): void {
    this.send(notification('textDocument/publishDiagnostics', { uri, diagnostics }))
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

  private answers(): Partial<Answers> {
    return this.options.answers()
  }

  private takeNotification(method: string, params: unknown): void {
    switch (method) {
      case 'textDocument/didOpen': {
        const { textDocument } = params as { textDocument: { uri: string, text: string } }
        this.documents.set(textDocument.uri, textDocument.text)
        void this.publishFor(textDocument.uri)
        return
      }
      case 'textDocument/didChange': {
        const { textDocument, contentChanges } = params as { textDocument: { uri: string }, contentChanges: { text: string }[] }
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
        const { token } = params as { token: string }
        for (const active of this.active.values()) {
          if (active.token === token) {
            active.abort.abort()
          }
        }
        return
      }
      default:
        if (!QUIET_NOTIFICATIONS.has(method)) {
          this.options.problem(`${this.flavorId}: the mock has no answer for the notification ${method}`)
        }
    }
  }

  private recordedDocument(uri: string): RecordedDocument | undefined {
    const text = this.documents.get(uri)
    return text === undefined ? undefined : this.fixture.documents[textKey(text)]
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
    const recorded = (): Diagnostic[] => {
      return this.recordedDocument(uri)?.diagnostics ?? this.lastDiagnostics.get(uri) ?? []
    }
    await delay(this.options.delayMs())
    try {
      const diagnostics = await (this.answers().diagnostics ?? ((_request, fallback) => fallback()))(documentRequest, recorded)
      this.lastDiagnostics.set(uri, diagnostics)
      if (this.documents.get(uri) === documentRequest.text) {
        this.publishDiagnostics(uri, diagnostics)
      }
    } catch (error) {
      this.options.problem(error instanceof Error ? error.message : String(error))
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
        this.options.problem(text)
      }
      this.send(errorResponse(message.id, { code: error instanceof Unanswered ? ErrorCodes.MethodNotFound : ErrorCodes.InternalError, message: text }))
    }
  }

  private async resultOf(message: RequestMessage): Promise<unknown> {
    const { method, params } = message
    if (EDITOR_FEATURES.has(method)) {
      const nothing = (): null => {
        return null
      }
      const feature = this.answers().feature
      return feature ? await feature({ session: this, method, params }, nothing) : null
    }
    switch (method) {
      case 'initialize':
        return this.fixture.initialize
      case 'shutdown':
        return null
      case 'semantifyr/live/session/info':
        return this.fixture.sessionInfo
      case 'semantifyr/live/session/verification/list':
        return { active: this.activeList() }
      case 'semantifyr/live/session/verification/cancel': {
        const { verificationId } = params as { verificationId: string }
        const found = [...this.active.values()].find((active) => {
          return active.verificationId === verificationId
        })
        if (found) {
          this.cancel(found.requestId)
        }
        return found !== undefined
      }
      case 'semantifyr/live/session/verification/cancel/all': {
        const ids = [...this.active.keys()]
        for (const id of ids) {
          this.cancel(id)
        }
        return ids.length
      }
      case 'semantifyr/live/session/document/read': {
        const { uri } = params as { uri: string }
        const recorded = (): string | null => {
          if (!(uri in this.fixture.reads)) {
            throw new Unanswered(`${this.flavorId}: no recorded text for ${uri}`)
          }
          return this.fixture.reads[uri] ?? null
        }
        const text = await (this.answers().read ?? ((_request, fallback) => fallback()))({ session: this, uri }, recorded)
        return { text }
      }
      case 'workspace/executeCommand':
        return this.command(message)
      default:
        throw new Unanswered(`${this.flavorId}: the mock has no answer for the request ${method}`)
    }
  }

  private async command(message: RequestMessage): Promise<unknown> {
    const { command, arguments: commandArguments } = message.params as { command: string, arguments: unknown[] }
    const capabilities = (this.fixture.initialize as { capabilities: { executeCommandProvider: { commands: string[] } } }).capabilities
    if (!capabilities.executeCommandProvider.commands.includes(command) && !command.endsWith('.compiled.oxsts')) {
      throw new Unanswered(`${this.flavorId}: the mock has no answer for the command ${command}`)
    }
    if (command.endsWith('.case.discover')) {
      return this.discover(commandArguments[0] as string)
    }
    if (command.endsWith('.compiled.oxsts')) {
      return this.compiled(commandArguments[0] as string)
    }
    const location = commandArguments[0] as { uri: string, range: Range, portfolio?: string }
    if (command.endsWith('.case.verify')) {
      return this.run(message.id, 'Verify', command, location)
    }
    if (command.endsWith('.validateWitness') || command.endsWith('.witness.validate')) {
      return this.run(message.id, 'Validate', command, location)
    }
    throw new Unanswered(`${this.flavorId}: the mock has no answer for the command ${command}`)
  }

  private async discover(uri: string): Promise<VerificationCaseSpecification[]> {
    const documentRequest = this.documentRequest(uri)
    const recorded = (): VerificationCaseSpecification[] => {
      const cases = this.recordedDocument(uri)?.cases ?? this.discovered.get(uri)
      if (cases === undefined) {
        throw new Unanswered(`${this.flavorId}: no recorded cases for this text of ${uri}`)
      }
      return cases
    }
    await delay(this.options.delayMs())
    const cases = await (this.answers().discover ?? ((_request, fallback) => fallback()))(documentRequest, recorded)
    this.discovered.set(uri, cases)
    return cases
  }

  private async compiled(uri: string): Promise<CompiledOxstsResult> {
    const documentRequest = this.documentRequest(uri)
    const recorded = (): CompiledOxstsResult => {
      const compiled = this.recordedDocument(uri)?.compiled ?? this.lastCompiled.get(uri)
      if (compiled === undefined) {
        throw new Unanswered(`${this.flavorId}: no recorded compiled OXSTS for this text of ${uri}`)
      }
      return compiled
    }
    const compiled = await (this.answers().compiled ?? ((_request, fallback) => fallback()))(documentRequest, recorded)
    this.lastCompiled.set(uri, compiled)
    return compiled
  }

  private caseAt(uri: string, range: Range): string | null {
    const cases = this.discovered.get(uri) ?? []
    return cases.find((candidate) => {
      return sameRange(candidate.location.range, range)
    })?.id ?? null
  }

  private activeList(): unknown[] {
    return [...this.active.values()].map((active) => {
      return {
        verificationId: active.verificationId,
        location: active.location,
        portfolioId: active.portfolioId,
        kind: active.kind,
        state: active.state,
        elapsed: 0,
      }
    })
  }

  private announce(): void {
    this.send(notification(CHANGED, { active: this.activeList() }))
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

  private async run(id: MessageId, kind: 'Verify' | 'Validate', command: string, location: { uri: string, range: Range, portfolio?: string }): Promise<unknown> {
    const abort = new AbortController()
    const active: Active = {
      requestId: id,
      verificationId: `verification-${this.nextVerificationId++}`,
      kind,
      location: { uri: location.uri, range: location.range },
      portfolioId: location.portfolio ?? 'smart-full',
      state: 'Queued',
      token: `progress-${id}`,
      abort,
    }
    this.active.set(id, active)
    this.announce()
    active.state = 'Running'
    this.announce()
    const caseRequest: CaseRequest = {
      session: this,
      command,
      uri: location.uri,
      range: location.range,
      portfolio: location.portfolio,
      caseId: this.caseAt(location.uri, location.range),
      signal: abort.signal,
    }
    const progress = kind === 'Verify' ? this.fixture.verifyProgress : this.fixture.validateProgress
    this.send(request(String(this.nextServerId++), 'window/workDoneProgress/create', { token: active.token }))
    this.progress(active.token, progress.slice(0, -1))
    let result: unknown
    try {
      const verify = this.answers().verify
      const validate = this.answers().validate
      const recordedResult = (): VerificationCaseResult => {
        return this.recordedResult(caseRequest)
      }
      const recordedValidation = (): WitnessValidationResult => {
        return this.recordedValidation(caseRequest)
      }
      if (kind === 'Verify') {
        result = verify ? await verify(caseRequest, recordedResult) : await delay(this.options.delayMs(), abort.signal).then(recordedResult)
      } else {
        result = validate ? await validate(caseRequest, recordedValidation) : await delay(this.options.delayMs(), abort.signal).then(recordedValidation)
      }
    } finally {
      this.progress(active.token, progress.slice(-1))
    }
    if (!this.active.has(id)) {
      return NO_ANSWER
    }
    this.active.delete(id)
    this.announce()
    if (abort.signal.aborted && kind === 'Validate') {
      return { status: 'errored', verdict: null, message: 'java.util.concurrent.CancellationException', backendId: null, portfolioId: active.portfolioId, metrics: null }
    }
    return result
  }

  private progress(token: string, values: readonly ProgressValue[]): void {
    for (const value of values) {
      this.send(notification('$/progress', { token, value }))
    }
  }

  private recordedResult(caseRequest: CaseRequest): VerificationCaseResult {
    const recorded = caseRequest.caseId === null ? undefined : this.fixture.results[caseRequest.caseId]
    const result = recorded ?? Object.values(this.fixture.results).find((candidate) => {
      return candidate.status === 'passed' && !candidate.trace
    })
    if (result === undefined) {
      throw new Unanswered(`${this.flavorId}: no recorded result for ${caseRequest.caseId}`)
    }
    return result
  }

  private recordedValidation(caseRequest: CaseRequest): WitnessValidationResult {
    const recorded = this.fixture.validations[caseRequest.caseId ?? caseRequest.uri] ?? Object.values(this.fixture.validations).find((candidate) => {
      return candidate.status === 'valid'
    })
    if (recorded === undefined) {
      throw new Unanswered(`${this.flavorId}: no recorded validation for ${caseRequest.caseId ?? caseRequest.uri}`)
    }
    return recorded
  }
}

const NO_ANSWER = Symbol('no answer')
