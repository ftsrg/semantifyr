/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from 'vitest'
import { VerificationStatus, type VerificationCaseResult } from '@semantifyr/editor-common'
import { createSysmlBridge, mapperProblems, SYSML_OXSTS_URI } from '../../lib/session/sysmlBridge'
import type { LspClient } from '../../lib/verification/engine'

const fileUri = 'file:///workspace/snippet.sysml'
const sysmlRange = { start: { line: 4, character: 0 }, end: { line: 9, character: 1 } }
const oxstsRange = { start: { line: 40, character: 0 }, end: { line: 60, character: 1 } }
const callTrace = { steps: [] }
const witnessState = { steps: [] }
const nameMap = { modelHash: 'abc' }
const witnessUri = 'file:///workspace/.artifacts/case/witness.oxsts'

interface Command {
  command: string
  arguments: unknown[]
}

function failedResult(): VerificationCaseResult {
  return {
    status: VerificationStatus.Failed,
    message: null,
    backendId: 'theta',
    portfolioId: 'smart',
    metrics: null,
    trace: {
      callTrace: callTrace as never,
      witnessState: witnessState as never,
      witnessUri,
    },
  }
}

function stubWorker(oxsts: string | null = 'mapped'): LspClient & { requests: Command[] } {
  const requests: Command[] = []
  return {
    requests,
    sendNotification: () => {},
    sendRequest: (_method, params) => {
      const request = params as Command
      requests.push(request)
      switch (request.command) {
        case 'syside.semantifyr.sysml.oxsts':
          return Promise.resolve({ oxsts: oxsts ?? undefined, nameMap, diagnostics: [] })
        case 'syside.semantifyr.sysml.verificationCases':
          return Promise.resolve([{ name: 'LampNeverLit', range: sysmlRange }])
        case 'syside.semantifyr.sysml.trace':
          return Promise.resolve('part def LampNeverLitTrace;')
        default:
          return Promise.reject(new Error(`unexpected ${request.command}`))
      }
    },
  }
}

function stubOxsts(result = failedResult()) {
  const commands: Command[] = []
  const notifications: { method: string; params: unknown }[] = []
  const client: LspClient = {
    sendNotification: (method, params) => {
      notifications.push({ method, params })
    },
    sendRequest: (_method, params) => {
      const request = params as Command
      commands.push(request)
      switch (request.command) {
        case 'oxsts.case.discover':
          return Promise.resolve([
            {
              id: 'semantifyr::sysml::model::LampNeverLit',
              label: 'LampNeverLit',
              location: { uri: SYSML_OXSTS_URI, range: oxstsRange },
            },
          ])
        case 'oxsts.case.verify':
          return Promise.resolve(result)
        case 'oxsts.case.validateWitness':
          return Promise.resolve({ status: 'valid', verdict: 'failed', message: null, backendId: null, portfolioId: null, metrics: null })
        default:
          return Promise.reject(new Error(`unexpected ${request.command}`))
      }
    },
  }
  return { client, commands, notifications }
}

function verifyRequest(worker: LspClient, oxsts: LspClient) {
  const bridge = createSysmlBridge({ fileUri, getWorkerClient: () => worker, getOxstsClient: () => oxsts })
  const verifyArguments = { uri: fileUri, range: sysmlRange, portfolio: 'smart' }
  return {
    bridge,
    run: () => bridge.client.sendRequest('workspace/executeCommand', { command: 'sysml.case.verify', arguments: [verifyArguments] }) as Promise<VerificationCaseResult>,
  }
}

describe('sysml bridge', () => {
  it('discovers the verification case elements in the edited document', async () => {
    const bridge = createSysmlBridge({ fileUri, getWorkerClient: () => stubWorker(), getOxstsClient: () => undefined })

    await expect(bridge.client.sendRequest('workspace/executeCommand', { command: 'sysml.case.discover', arguments: [fileUri] })).resolves.toEqual([
      { id: 'LampNeverLit', label: 'LampNeverLit', location: { uri: fileUri, range: sysmlRange } },
    ])
  })

  it('verifies the mapped case through the OXSTS session and lifts the trace', async () => {
    const worker = stubWorker()
    const oxsts = stubOxsts()
    const { run, bridge } = verifyRequest(worker, oxsts.client)

    const result = await run()

    expect(oxsts.notifications).toEqual([
      {
        method: 'textDocument/didOpen',
        params: { textDocument: { uri: SYSML_OXSTS_URI, languageId: 'oxsts', version: 1, text: 'mapped' } },
      },
    ])
    expect(oxsts.commands[1]).toEqual({
      command: 'oxsts.case.verify',
      arguments: [{ uri: SYSML_OXSTS_URI, range: oxstsRange, portfolio: 'smart' }],
    })
    expect(worker.requests.at(-1)).toEqual({
      command: 'syside.semantifyr.sysml.trace',
      arguments: [{ uri: fileUri, caseName: 'LampNeverLit', witness: witnessState, trace: callTrace, nameMap }],
    })
    const traceUri = result.trace?.witnessUri ?? ''
    expect(traceUri).toBe('file:///workspace/traces/LampNeverLit.trace.sysml')
    await expect(bridge.client.sendRequest('semantifyr/live/session/document/read', { uri: traceUri })).resolves.toEqual({ text: 'part def LampNeverLitTrace;' })
  })

  it('sends the name map of the mapping that was verified, not the current one', async () => {
    const worker = stubWorker()
    const stubbed = worker.sendRequest
    let hashes = 0
    worker.sendRequest = (method, params) => {
      return (params as Command).command === 'syside.semantifyr.sysml.oxsts'
        ? Promise.resolve({ oxsts: 'mapped', nameMap: { modelHash: `hash${++hashes}` }, diagnostics: [] })
        : stubbed(method, params)
    }
    const oxsts = stubOxsts()
    const bridge = createSysmlBridge({ fileUri, getWorkerClient: () => worker, getOxstsClient: () => oxsts.client })
    const verifying = oxsts.client.sendRequest
    oxsts.client.sendRequest = async (method, params) => {
      if ((params as Command).command === 'oxsts.case.verify') {
        await bridge.readMapping()
      }
      return verifying(method, params)
    }

    await bridge.client.sendRequest('workspace/executeCommand', {
      command: 'sysml.case.verify',
      arguments: [{ uri: fileUri, range: sysmlRange }],
    })

    expect(worker.requests.at(-1)?.arguments).toEqual([
      { uri: fileUri, caseName: 'LampNeverLit', witness: witnessState, trace: callTrace, nameMap: { modelHash: 'hash1' } },
    ])
  })

  it('sends a full-text change for a changed mapping and reopens on a new session', async () => {
    let oxstsText = 'first'
    const worker = stubWorker()
    const stubbed = worker.sendRequest
    worker.sendRequest = (method, params) => {
      const request = params as Command
      return request.command === 'syside.semantifyr.sysml.oxsts' ? Promise.resolve({ oxsts: oxstsText, diagnostics: [] }) : stubbed(method, params)
    }
    let session = stubOxsts()
    const firstSession = session
    const bridge = createSysmlBridge({ fileUri, getWorkerClient: () => worker, getOxstsClient: () => session.client })
    const verify = () => bridge.client.sendRequest('workspace/executeCommand', { command: 'sysml.case.verify', arguments: [{ uri: fileUri, range: sysmlRange }] })

    await verify()
    oxstsText = 'second'
    await verify()
    session = stubOxsts()
    await verify()

    expect(firstSession.notifications.map((notification) => notification.method)).toEqual([
      'textDocument/didOpen',
      'textDocument/didChange',
    ])
    expect(firstSession.notifications[1]?.params).toEqual({
      textDocument: { uri: SYSML_OXSTS_URI, version: 2 },
      contentChanges: [{ text: 'second' }],
    })
    expect(session.notifications.map((notification) => notification.method)).toEqual(['textDocument/didOpen'])
  })

  it('errors without reaching the backend when no case is at the requested range', async () => {
    const oxsts = stubOxsts()
    const bridge = createSysmlBridge({ fileUri, getWorkerClient: () => stubWorker(), getOxstsClient: () => oxsts.client })
    const otherRange = { start: { line: 12, character: 0 }, end: { line: 14, character: 1 } }

    const result = (await bridge.client.sendRequest('workspace/executeCommand', { command: 'sysml.case.verify', arguments: [{ uri: fileUri, range: otherRange }] })) as VerificationCaseResult

    expect(result.status).toBe(VerificationStatus.Errored)
    expect(oxsts.commands).toEqual([])
  })

  it('does not reach the backend when the model has no OXSTS', async () => {
    const worker = stubWorker(null)
    const oxsts = stubOxsts()
    const { run } = verifyRequest(worker, oxsts.client)

    const result = await run()

    expect(result.status).toBe(VerificationStatus.Errored)
    expect(oxsts.commands).toEqual([])
    expect(oxsts.notifications).toEqual([])
  })

  it('keeps the verdict and says so when the trace cannot be lifted', async () => {
    const worker = stubWorker()
    const stubbed = worker.sendRequest
    worker.sendRequest = (method, params) => {
      return (params as Command).command === 'syside.semantifyr.sysml.trace' ? Promise.reject(new Error('no such element')) : stubbed(method, params)
    }
    const { run, bridge } = verifyRequest(worker, stubOxsts().client)

    const result = await run()

    expect(result.status).toBe(VerificationStatus.Failed)
    expect(result.message).toContain('no such element')
    await expect(bridge.client.sendRequest('semantifyr/live/session/document/read', { uri: result.trace?.witnessUri })).resolves.toEqual({ text: null })
  })

  it('validates the OXSTS witness behind the trace', async () => {
    const oxsts = stubOxsts()
    const { run, bridge } = verifyRequest(stubWorker(), oxsts.client)
    const result = await run()

    await bridge.client.sendRequest('workspace/executeCommand', {
      command: 'sysml.witness.validate',
      arguments: [{ uri: result.trace?.witnessUri, range: oxstsRange }],
    })

    expect(oxsts.commands.at(-1)).toEqual({
      command: 'oxsts.case.validateWitness',
      arguments: [{ uri: witnessUri, range: oxstsRange }],
    })
  })

  it('reports a running OXSTS case at its SysML location', async () => {
    const { run, bridge } = verifyRequest(stubWorker(), stubOxsts().client)
    await run()
    const params = { active: [{ verificationId: '1', location: { uri: SYSML_OXSTS_URI, range: oxstsRange } }] }

    expect(bridge.translateNotification('semantifyr/live/session/verification/changed', params)).toEqual({
      active: [{ verificationId: '1', location: { uri: fileUri, range: sysmlRange } }],
    })
  })

  it('reports a validation of the OXSTS witness at the trace the page knows', async () => {
    const { run, bridge } = verifyRequest(stubWorker(), stubOxsts().client)
    const result = await run()
    const witnessRange = { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } }
    const params = { active: [{ verificationId: '2', kind: 'Validate', location: { uri: witnessUri, range: witnessRange } }] }

    expect(bridge.translateNotification('semantifyr/live/session/verification/changed', params)).toEqual({
      active: [{ verificationId: '2', kind: 'Validate', location: { uri: result.trace?.witnessUri, range: witnessRange } }],
    })
  })

  it('renders only the mapper diagnostics, keeping their severity', () => {
    const range = { start: { line: 1, character: 2 }, end: { line: 1, character: 8 } }

    expect(mapperProblems({
      diagnostics: [
        { range, message: 'unsupported', severity: 2, source: 'semantifyr', code: 'unsupported-construct' },
        { range, message: 'parse error', severity: 1, source: 'sysml' },
      ],
    })).toEqual([
      { severity: 'warning', startLine: 1, startColumn: 2, endLine: 1, endColumn: 8, message: 'unsupported', source: 'semantifyr', code: 'unsupported-construct' },
    ])
  })
})
