/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { type ReportVerdict, type VerificationCaseResult, VerificationStatus, type WitnessValidationResult } from '@semantifyr/editor-common'
import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'

import type { ProgressValue } from '../../__ui__/mock/fixtures'
import { encodeMessage, notification, request, response, type Message } from '../../__ui__/mock/jsonRpc'
import { fixturesOf, isRunFinished, type RecordedMessage, type Transcript } from '../../__ui__/mock/transcript'
import type { VerificationCaseState } from '../../lib/verification/engine'
import { caseRowClassName, caseRowOf } from '../../lib/workspace/caseRows'
import { ValidationMark } from '../../ui/marks'
import { reportOf } from '../fixtures/reports'

const URI = 'file:///workspace/snippet.semantifyr'
const WITNESS_URI = 'file:///workspace/.artifacts/A/witness.semantifyr'

function rangeAt(line: number) {
  return {
    start: { line, character: 0 },
    end: { line, character: 1 },
  }
}

function client(message: Message): RecordedMessage {
  return { from: 'client', text: encodeMessage(message) }
}

function server(message: Message): RecordedMessage {
  return { from: 'server', text: encodeMessage(message) }
}

function created(id: number, token: string): RecordedMessage {
  return server(request(id, 'window/workDoneProgress/create', { token }))
}

function progressed(token: string, value: ProgressValue): RecordedMessage {
  return server(notification('$/progress', { token, value }))
}

function verify(id: number, line: number): RecordedMessage {
  return client(request(id, 'workspace/executeCommand', { command: 'oxsts.case.verify', arguments: [{ uri: URI, range: rangeAt(line) }] }))
}

function verified(id: number, verdict: ReportVerdict): RecordedMessage {
  const result: VerificationCaseResult = {
    status: null,
    message: null,
    portfolioId: null,
    report: reportOf({ verdict, backendId: 'theta', seconds: 1 }),
    witnessUri: null,
    traceUri: null,
  }
  return server(response(id, result))
}

function validated(id: number): RecordedMessage {
  const result: WitnessValidationResult = {
    status: 'valid',
    message: null,
    portfolioId: null,
    report: reportOf({ verdict: 'Failed', backendId: 'theta', seconds: 1, kind: 're-check' }),
  }
  return server(response(id, result))
}

function transcriptOf(messages: RecordedMessage[]): Transcript {
  return {
    rest: [],
    sessions: [
      {
        url: 'ws://127.0.0.1/ws/lsp/oxsts?kind=full&language=oxsts',
        messages: [
          client(notification('textDocument/didOpen', { textDocument: { uri: URI, text: 'model' } })),
          client(request(1, 'workspace/executeCommand', { command: 'oxsts.case.discover', arguments: [URI] })),
          server(response(1, [
            { id: 'A', location: { uri: URI, range: rangeAt(1) } },
            { id: 'B', location: { uri: URI, range: rangeAt(2) } },
          ])),
          ...messages,
        ],
      },
    ],
  }
}

const firstVerification: ProgressValue[] = [
  { kind: 'begin', title: 'Verifying class A' },
  { kind: 'end' },
]

const secondVerification: ProgressValue[] = [
  { kind: 'begin', title: 'Verifying class B' },
  { kind: 'report', message: 'Compiling' },
  { kind: 'report', message: 'Running' },
  { kind: 'end' },
]

const validation: ProgressValue[] = [
  { kind: 'begin', title: 'Validating witness' },
  { kind: 'report', message: 'Re-verifying' },
  { kind: 'end' },
]

describe('the fixtures derived from a recorded transcript', () => {
  it('keep the whole verification and validation series when runs interleave and their progress is created under later requests', () => {
    const fixtures = fixturesOf(transcriptOf([
      verify(2, 1),
      verify(3, 2),
      created(100, 'a'),
      ...firstVerification.map((value) => {
        return progressed('a', value)
      }),
      verified(2, 'Failed'),
      client(request(4, 'workspace/executeCommand', { command: 'oxsts.case.validateWitness', arguments: [{ uri: WITNESS_URI, range: rangeAt(0) }] })),
      created(101, 'b'),
      created(102, 'v'),
      ...secondVerification.map((value) => {
        return progressed('b', value)
      }),
      ...validation.map((value) => {
        return progressed('v', value)
      }),
      validated(4),
      verified(3, 'Passed'),
    ]))

    const fixture = fixtures.get('oxsts')!
    expect(Object.keys(fixture.results)).toEqual([
      'A',
      'B',
    ])
    expect(fixture.verifyProgress).toEqual(secondVerification)
    expect(fixture.validateProgress).toEqual(validation)
  })

  it('keep the longest finished verification series when a shorter one finishes after it', () => {
    const fixtures = fixturesOf(transcriptOf([
      verify(2, 1),
      created(100, 'b'),
      ...secondVerification.map((value) => {
        return progressed('b', value)
      }),
      verified(2, 'Failed'),
      verify(3, 2),
      created(101, 'a'),
      ...firstVerification.map((value) => {
        return progressed('a', value)
      }),
      verified(3, 'Passed'),
    ]))

    expect(fixtures.get('oxsts')!.verifyProgress).toEqual(secondVerification)
  })

  it('skip a verification series that never finished, however long it is', () => {
    const fixtures = fixturesOf(transcriptOf([
      verify(2, 1),
      created(100, 'b'),
      ...secondVerification.slice(0, -1).map((value) => {
        return progressed('b', value)
      }),
      verified(2, 'Failed'),
      verify(3, 2),
      created(101, 'a'),
      ...firstVerification.map((value) => {
        return progressed('a', value)
      }),
      verified(3, 'Passed'),
    ]))

    expect(fixtures.get('oxsts')!.verifyProgress).toEqual(firstVerification)
  })

  it('never file a validation series as the verification series, though it is longer', () => {
    const fixtures = fixturesOf(transcriptOf([
      verify(2, 1),
      created(100, 'a'),
      ...firstVerification.map((value) => {
        return progressed('a', value)
      }),
      verified(2, 'Failed'),
      client(request(3, 'workspace/executeCommand', { command: 'oxsts.case.validateWitness', arguments: [{ uri: WITNESS_URI, range: rangeAt(0) }] })),
      created(101, 'v'),
      ...validation.map((value) => {
        return progressed('v', value)
      }),
      validated(3),
    ]))

    const fixture = fixtures.get('oxsts')!
    expect(fixture.verifyProgress).toEqual(firstVerification)
    expect(fixture.validateProgress).toEqual(validation)
  })

  it('leave out a case whose report says the run errored', () => {
    const fixtures = fixturesOf(transcriptOf([
      verify(2, 1),
      verify(3, 2),
      created(100, 'a'),
      ...firstVerification.map((value) => {
        return progressed('a', value)
      }),
      verified(2, 'Errored'),
      verified(3, 'Passed'),
    ]))

    expect(Object.keys(fixtures.get('oxsts')!.results)).toEqual([
      'B',
    ])
  })

  it('fail naming the flavor when it verified a case and no verification series finished', () => {
    const transcript = transcriptOf([
      verify(2, 1),
      created(100, 'a'),
      progressed('a', { kind: 'begin', title: 'Verifying class A' }),
      verified(2, 'Passed'),
    ])

    expect(() => {
      return fixturesOf(transcript)
    }).toThrow('oxsts verified a case and has no finished verification progress')
  })

  it('fail naming the flavor when it validated a witness and no validation series finished', () => {
    const transcript = transcriptOf([
      verify(2, 1),
      created(100, 'a'),
      ...firstVerification.map((value) => {
        return progressed('a', value)
      }),
      verified(2, 'Failed'),
      client(request(3, 'workspace/executeCommand', { command: 'oxsts.case.validateWitness', arguments: [{ uri: WITNESS_URI, range: rangeAt(0) }] })),
      validated(3),
    ])

    expect(() => {
      return fixturesOf(transcript)
    }).toThrow('oxsts validated a witness and has no finished validation progress')
  })
})

function caseState(status: VerificationCaseState['status'], extra: Partial<VerificationCaseState> = {}): VerificationCaseState {
  return {
    caseInfo: { id: 'A', label: 'A', location: { uri: URI, range: rangeAt(1) } },
    status,
    resultStatus: status,
    ...extra,
  }
}

function rowClassNameOf(state: VerificationCaseState): string {
  return caseRowClassName(caseRowOf(state), false)
}

function validationMarkClassNamesOf(state: VerificationCaseState): string[] {
  const validation = caseRowOf(state).validation
  if (validation === null) {
    return []
  }
  return [...renderToStaticMarkup(ValidationMark({ state: validation })).matchAll(/class="(validation-mark[^"]*)"/g)].map((match) => {
    return match[1] ?? ''
  })
}

describe('the recorder\'s wait for a finished run', () => {
  it.each(Object.values(VerificationStatus).filter((status) => {
    return status !== VerificationStatus.Cancelled
  }))('ends on a row whose run ended %s', (status) => {
    expect(isRunFinished([rowClassNameOf(caseState(status))], [])).toBe(true)
  })

  it('goes on while a row is queued, runs or runs again with its last verdict shown', () => {
    expect(isRunFinished([rowClassNameOf(caseState('running'))], [])).toBe(false)
    expect(isRunFinished([rowClassNameOf(caseState('running', { serverState: 'Running' }))], [])).toBe(false)
    expect(isRunFinished([rowClassNameOf(caseState('failed', { status: 'running', serverState: 'Running' }))], [])).toBe(false)
  })

  it('goes on while a witness waits for its validation or is validated, and ends once it is', () => {
    const queued = caseState('failed', { validating: true })
    const validating = caseState('failed', { validating: true, validationServerState: 'Running' })
    const validated = caseState('failed', { witnessValidation: 'valid' })

    expect(isRunFinished([rowClassNameOf(queued)], validationMarkClassNamesOf(queued))).toBe(false)
    expect(isRunFinished([rowClassNameOf(validating)], validationMarkClassNamesOf(validating))).toBe(false)
    expect(isRunFinished([rowClassNameOf(validated)], validationMarkClassNamesOf(validated))).toBe(true)
  })

  it('goes on before any row shows', () => {
    expect(isRunFinished([], [])).toBe(false)
  })
})
