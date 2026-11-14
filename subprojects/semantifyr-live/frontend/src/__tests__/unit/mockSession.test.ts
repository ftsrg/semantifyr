/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it, vi } from 'vitest'

import { flavorFixture, textKey } from '../../__ui__/mock/fixtures'
import { encodeMessage, notification, parseMessage, request, type Message, type ResponseMessage } from '../../__ui__/mock/jsonRpc'
import { MockSession, type Answers } from '../../__ui__/mock/session'

const URI = 'file:///workspace/snippet.oxsts'

// the fixtures are recorded from a checkout with LF line ends
const INTERSECTION = readFileSync(join(import.meta.dirname, '../../snippets/imported/tutorial/intersection.oxsts'), 'utf-8').replaceAll('\r\n', '\n')

interface Harness {
  session: MockSession
  sent: Message[]
  problems: string[]
  closes: { code: number, reason: string }[]
  answered: (id: number) => Promise<ResponseMessage>
}

function harness(answers: Partial<Answers> = {}): Harness {
  const sent: Message[] = []
  const problems: string[] = []
  const closes: { code: number, reason: string }[] = []
  const session = new MockSession({
    flavorId: 'oxsts',
    url: 'ws://127.0.0.1/ws/lsp/oxsts?kind=full&language=oxsts',
    answers: () => {
      return answers
    },
    delayMs: () => {
      return 0
    },
    problem: (problem) => {
      problems.push(problem)
    },
  })
  session.attach({
    send: (frame) => {
      sent.push(parseMessage(frame))
    },
    close: (code, reason) => {
      closes.push({ code, reason })
    },
  })
  return {
    session,
    sent,
    problems,
    closes,
    answered: async (id) => {
      await vi.waitFor(() => {
        expect(sent.some((message) => {
          return 'id' in message && message.id === id && !('method' in message)
        })).toBe(true)
      })
      return sent.find((message) => {
        return 'id' in message && message.id === id && !('method' in message)
      }) as ResponseMessage
    },
  }
}

describe('the JSON-RPC framing of the session socket', () => {
  it('reads one message per frame and writes it back unchanged', () => {
    const message = request(3, 'workspace/executeCommand', { command: 'oxsts.case.discover', arguments: [URI] })

    expect(parseMessage(encodeMessage(message))).toEqual(message)
    expect(parseMessage(Buffer.from(encodeMessage(notification('initialized', {}))))).toEqual({ jsonrpc: '2.0', method: 'initialized', params: {} })
  })

  it('refuses a frame that is not JSON-RPC 2.0', () => {
    expect(() => {
      return parseMessage('{"id":1}')
    }).toThrow(/JSON-RPC 2.0/)
  })
})

describe('the mock session', () => {
  it('starts the session with the recorded answer of the server', async () => {
    const { session, answered } = harness()

    session.receive(encodeMessage(request(0, 'initialize', { capabilities: {} })))

    expect((await answered(0)).result).toEqual(flavorFixture('oxsts').initialize)
  })

  it('answers the discovery of a recorded text with its recorded cases, and an edited text with the cases it answered last', async () => {
    const { session, answered, problems } = harness()
    const discover = (id: number): void => {
      session.receive(encodeMessage(request(id, 'workspace/executeCommand', { command: 'oxsts.case.discover', arguments: [URI] })))
    }
    session.receive(encodeMessage(notification('textDocument/didOpen', { textDocument: { uri: URI, languageId: 'oxsts', version: 1, text: INTERSECTION } })))
    discover(1)
    const recorded = (await answered(1)).result

    session.receive(encodeMessage(notification('textDocument/didChange', { textDocument: { uri: URI, version: 2 }, contentChanges: [{ text: `${INTERSECTION}\n` }] })))
    discover(2)

    expect(recorded).toEqual(flavorFixture('oxsts').documents[textKey(INTERSECTION)]?.cases)
    expect((recorded as unknown[]).length).toBeGreaterThan(0)
    expect((await answered(2)).result).toEqual(recorded)
    expect(problems).toEqual([])
  })

  it('fails loudly on the discovery of a text it never answered for', async () => {
    const { session, answered, problems } = harness()

    session.receive(encodeMessage(notification('textDocument/didOpen', { textDocument: { uri: URI, languageId: 'oxsts', version: 1, text: 'package unrecorded' } })))
    session.receive(encodeMessage(request(1, 'workspace/executeCommand', { command: 'oxsts.case.discover', arguments: [URI] })))

    expect((await answered(1)).error?.message).toMatch(/no recorded cases/)
    expect(problems).toHaveLength(1)
  })

  it('fails loudly on a request it has no answer for, and stays silent on the notifications it knows', async () => {
    const { session, answered, problems } = harness()

    session.receive(encodeMessage(notification('initialized', {})))
    session.receive(encodeMessage(request(5, 'textDocument/unknownFeature', {})))

    expect((await answered(5)).error?.code).toBe(-32601)
    expect(problems).toEqual(['oxsts: the mock has no answer for the request textDocument/unknownFeature'])
  })

  it('runs a verification as the server does: active list, progress, the result, and a cancel answered as cancelled', async () => {
    const releases: (() => void)[] = []
    const { session, sent, answered } = harness({
      verify: async (caseRequest, recorded) => {
        await new Promise<void>((resolve) => {
          releases.push(resolve)
          caseRequest.signal.addEventListener('abort', () => {
            resolve()
          })
        })
        return recorded()
      },
    })
    const range = { start: { line: 1, character: 0 }, end: { line: 1, character: 4 } }
    session.receive(encodeMessage(request(1, 'workspace/executeCommand', { command: 'oxsts.case.verify', arguments: [{ uri: URI, range, portfolio: 'theta' }] })))
    session.receive(encodeMessage(request(2, 'workspace/executeCommand', { command: 'oxsts.case.verify', arguments: [{ uri: URI, range, portfolio: 'theta' }] })))
    await vi.waitFor(() => {
      expect(sent.filter((message) => {
        return 'method' in message && message.method === 'window/workDoneProgress/create'
      })).toHaveLength(2)
    })

    session.receive(encodeMessage(notification('$/cancelRequest', { id: 2 })))
    for (const release of releases) {
      release()
    }

    expect((await answered(2)).error?.code).toBe(-32800)
    expect((await answered(1)).result).toMatchObject({ status: 'passed' })
    expect(session.commands('oxsts.case.verify')).toHaveLength(2)
    const changed = sent.filter((message) => {
      return 'method' in message && message.method === 'semantifyr/live/session/verification/changed'
    }).map((message) => {
      return ((message as { params: { active: { portfolioId: string }[] } }).params.active).map((active) => {
        return active.portfolioId
      })
    })
    expect(changed.at(-1)).toEqual([])
    expect(changed.flat()).toContain('theta')
  })

  it('closes with the code a test gives and sends nothing after', () => {
    const { session, sent, closes } = harness()

    session.close(4000, 'Session terminated')
    session.publishDiagnostics(URI, [])

    expect(closes).toEqual([{ code: 4000, reason: 'Session terminated' }])
    expect(sent).toEqual([])
  })
})
