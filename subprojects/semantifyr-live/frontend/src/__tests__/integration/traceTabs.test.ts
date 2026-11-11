/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { afterEach, describe, expect, it, vi } from 'vitest'
import type { VerificationCaseResult, VerificationCaseSpecification } from '@semantifyr/editor-common'
import type { LspClient } from '../../lib/verification/engine'
import { followTraceTabs } from '../../trace/traceTabs'
import { traceTabOf } from '../../trace/TextView'
import { createTestLive, type TestLive } from '../helpers/live'
import { failedResult, passedResult, sampleTrace } from '../fixtures/verification-results'

// Goal: an open trace text shows the last trace of its case and nothing older

vi.mock('vscode', () => {
  return {}
})

vi.mock('../../lib/session/monaco', () => {
  return {}
})

vi.mock('monaco-editor', () => {
  return {}
})

const CASE: VerificationCaseSpecification = {
  id: 'a',
  label: 'a',
  location: {
    uri: 'file:///workspace/snippet.oxsts',
    range: { start: { line: 1, character: 0 }, end: { line: 1, character: 1 } },
  },
}

// verifies the case with the result given last, and reads each witness by its address
function sessionClient(): { client: LspClient, answer: (result: VerificationCaseResult) => void } {
  let result = failedResult
  return {
    client: {
      sendRequest: async (method, parameters) => {
        if (method === 'semantifyr/live/session/document/read') {
          return { text: `witness at ${(parameters as { uri: string }).uri}` }
        }
        const { command } = parameters as { command: string }
        if (command === 'oxsts.case.discover') {
          return [CASE]
        }
        if (command === 'oxsts.case.verify') {
          return result
        }
        return null
      },
      sendNotification: () => {},
    },
    answer: (next) => {
      result = next
    },
  }
}

async function verified(test: TestLive): Promise<void> {
  test.live.verifyCase('a')
  await vi.waitFor(() => {
    expect(test.live.verification.get().get(test.live.workspace.get().documentKey!)?.phase).toBe('done')
  })
}

function witnessTab(test: TestLive): ReturnType<typeof traceTabOf> | undefined {
  return test.live.tabs.state.get().tabs.find((tab) => {
    return tab.id === 'trace-witness'
  })
}

describe('an open trace text', () => {
  let open: TestLive | null = null

  afterEach(async () => {
    await open?.live.dispose()
    open = null
  })

  it('follows a rerun of its case, and closes when the case has no trace or another model is shown', async () => {
    const session = sessionClient()
    open = createTestLive(session.client)
    open.live.start('/oxsts')
    await vi.waitFor(() => {
      expect(open!.live.workspace.get().runtime).not.toBeNull()
    })
    const { documentKey } = open.live.workspace.get()
    const record = open.live.documents.get().get(documentKey!)!
    open.models.open(record.uri, record.text)
    open.runtime().connect()
    await vi.waitFor(() => {
      expect(open!.live.verification.get().get(documentKey!)?.discovery).toBe('discovered')
    })
    await verified(open)
    const firstTrace = open.live.verification.get().get(documentKey!)!.cases[0]!.trace!
    const stop = followTraceTabs(open.live, open.runtime(), 'oxsts')
    open.live.tabs.open(traceTabOf({ tabId: 'trace-witness', fileUri: 'file:///workspace/first/witness.oxsts', label: 'a.witness.oxsts', languageId: 'oxsts', banner: '' }, 'first witness', { documentKey: documentKey!, caseId: 'a', view: 'witness', shown: firstTrace }))

    session.answer({ ...failedResult, trace: { ...sampleTrace, witnessUri: 'file:///workspace/second/witness.oxsts' } })
    await verified(open)
    await vi.waitFor(() => {
      expect(witnessTab(open!)?.text).toBe('witness at file:///workspace/second/witness.oxsts')
    })
    expect(open.live.tabs.state.get().active).toBe('trace-witness')

    session.answer(passedResult)
    await verified(open)
    await vi.waitFor(() => {
      expect(witnessTab(open!)).toBeUndefined()
    })

    session.answer(failedResult)
    await verified(open)
    const trace = open.live.verification.get().get(documentKey!)!.cases[0]!.trace!
    open.live.tabs.open(traceTabOf({ tabId: 'trace-witness', fileUri: 'file:///workspace/third/witness.oxsts', label: 'a.witness.oxsts', languageId: 'oxsts', banner: '' }, 'third witness', { documentKey: documentKey!, caseId: 'a', view: 'witness', shown: trace }))
    const other = open.live.catalog.get().registry!.flavorOf('oxsts')!.examples[1]!
    open.live.selectModel('oxsts', other.id)
    expect(witnessTab(open)).toBeUndefined()
    stop()
  })
})
