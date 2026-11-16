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

import { textKey, type Diagnostic, type FlavorFixture, type ProgressValue } from './fixtures.ts'
import { isNotification, isRequest, parseMessage, type Message, type MessageId, type RequestMessage } from './jsonRpc.ts'

export interface RecordedMessage {
  from: 'client' | 'server'
  text: string
}

export interface RecordedSession {
  url: string
  messages: RecordedMessage[]
}

interface RecordedRest {
  url: string
  method: string
  status: number
  body: string
}

export interface Transcript {
  rest: RecordedRest[]
  sessions: RecordedSession[]
}

interface Pending {
  message: RequestMessage
  key: string | null
}

function emptyFixture(): FlavorFixture {
  return { initialize: null, sessionInfo: null, documents: {}, results: {}, validations: {}, reads: {}, verifyProgress: [], validateProgress: [] }
}

function sameStart(left: Range, right: Range): boolean {
  return left.start.line === right.start.line && left.start.character === right.start.character
}

// a run's progress names its kind in the title of its begin; only the leading verb is matched, the rest is reworded freely
function runKindOf(title: string | undefined): 'verify' | 'validate' | null {
  if (title?.startsWith('Verif')) {
    return 'verify'
  }
  if (title?.startsWith('Validat')) {
    return 'validate'
  }
  return null
}

// the answers the mock replays, filed by flavor and by the text of the document they were given for
export function fixturesOf(transcript: Transcript): Map<string, FlavorFixture> {
  const fixtures = new Map<string, FlavorFixture>()
  for (const session of transcript.sessions) {
    const flavorId = decodeURIComponent(new URL(session.url).pathname.split('/').at(-1) ?? '')
    let fixture = fixtures.get(flavorId)
    if (!fixture) {
      fixture = emptyFixture()
      fixtures.set(flavorId, fixture)
    }
    const texts = new Map<string, string>()
    const discovered = new Map<string, VerificationCaseSpecification[]>()
    const pending = new Map<MessageId, Pending>()
    const progress = new Map<string, ProgressValue[]>()
    const documentOf = (uri: string) => {
      const text = texts.get(uri)
      if (text === undefined) {
        return null
      }
      const key = textKey(text)
      fixture.documents[key] ??= {}
      return fixture.documents[key]
    }
    const caseAt = (uri: string, range: Range): string | null => {
      return discovered.get(uri)?.find((candidate) => {
        return sameStart(candidate.location.range, range)
      })?.id ?? null
    }
    for (const recorded of session.messages) {
      const message: Message = parseMessage(recorded.text)
      if (recorded.from === 'client') {
        if (isNotification(message)) {
          if (message.method === 'textDocument/didOpen') {
            const { textDocument } = message.params as { textDocument: { uri: string, text: string } }
            texts.set(textDocument.uri, textDocument.text)
          }
          if (message.method === 'textDocument/didChange') {
            const { textDocument, contentChanges } = message.params as { textDocument: { uri: string }, contentChanges: { text: string }[] }
            texts.set(textDocument.uri, contentChanges.at(-1)?.text ?? '')
          }
        } else if (isRequest(message)) {
          const argument = (message.params as { arguments?: unknown[] } | undefined)?.arguments?.[0]
          const uri = typeof argument === 'string' ? argument : (argument as { uri?: string } | undefined)?.uri
          pending.set(message.id, { message, key: uri !== undefined && texts.has(uri) ? textKey(texts.get(uri) ?? '') : null })
        }
        continue
      }
      if (isNotification(message)) {
        if (message.method === 'textDocument/publishDiagnostics') {
          const { uri, diagnostics } = message.params as { uri: string, diagnostics: Diagnostic[] }
          const document = documentOf(uri)
          if (document) {
            document.diagnostics = diagnostics
          }
        }
        if (message.method === '$/progress') {
          const { token, value } = message.params as { token: string, value: ProgressValue }
          progress.set(token, [...progress.get(token) ?? [], value])
        }
        continue
      }
      if (isRequest(message)) {
        continue
      }
      const request = pending.get(message.id ?? -1)
      if (!request || message.error !== undefined) {
        continue
      }
      pending.delete(message.id ?? -1)
      const { method, params } = request.message
      if (method === 'initialize') {
        fixture.initialize = message.result
      } else if (method === 'semantifyr/live/session/info') {
        fixture.sessionInfo = message.result as FlavorFixture['sessionInfo']
      } else if (method === 'semantifyr/live/session/document/read') {
        fixture.reads[(params as { uri: string }).uri] = (message.result as { text: string | null } | null)?.text ?? null
      } else if (method === 'workspace/executeCommand') {
        const { command, arguments: commandArguments } = params as { command: string, arguments: unknown[] }
        if (command.endsWith('.case.discover')) {
          const uri = commandArguments[0] as string
          const cases = message.result as VerificationCaseSpecification[]
          discovered.set(uri, cases)
          if (request.key !== null) {
            fixture.documents[request.key] = { ...fixture.documents[request.key], cases }
          }
        } else if (command.endsWith('.compiled.oxsts')) {
          if (request.key !== null) {
            fixture.documents[request.key] = { ...fixture.documents[request.key], compiled: message.result as CompiledOxstsResult }
          }
        } else {
          const location = commandArguments[0] as { uri: string, range: Range }
          const caseId = caseAt(location.uri, location.range)
          if (command.endsWith('.case.verify')) {
            const result = message.result as VerificationCaseResult
            if (caseId !== null && result.status !== 'errored') {
              fixture.results[caseId] = result
            }
          } else {
            const result = message.result as WitnessValidationResult
            if (result.status !== 'errored') {
              fixture.validations[caseId ?? location.uri] = result
            }
          }
        }
      }
    }
    for (const values of progress.values()) {
      if (values.at(-1)?.kind !== 'end') {
        continue
      }
      const kind = runKindOf(values[0]?.title)
      if (kind === 'verify' && values.length > fixture.verifyProgress.length) {
        fixture.verifyProgress = values
      }
      if (kind === 'validate' && values.length > fixture.validateProgress.length) {
        fixture.validateProgress = values
      }
    }
  }
  const unfinished = [...fixtures].flatMap(([flavorId, fixture]) => {
    const missing = []
    if (Object.keys(fixture.results).length > 0 && fixture.verifyProgress.length === 0) {
      missing.push(`${flavorId} verified a case and has no finished verification progress`)
    }
    if (Object.keys(fixture.validations).length > 0 && fixture.validateProgress.length === 0) {
      missing.push(`${flavorId} validated a witness and has no finished validation progress`)
    }
    return missing
  })
  if (unfinished.length > 0) {
    throw new Error(unfinished.join('\n'))
  }
  return fixtures
}
