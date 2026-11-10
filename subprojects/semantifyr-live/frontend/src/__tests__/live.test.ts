/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { afterEach, describe, expect, it, vi } from 'vitest'
import type { VerificationCaseResult, VerificationCaseSpecification } from '@semantifyr/editor-common'
import type { LspClient, VerificationState } from '../lib/verification/engine'
import { NO_VERIFICATION } from '../lib/workspace/verification'
import { createTestLive, testFlavors, type TestLive } from './helpers/live'
import { failedResult, passedResult } from './fixtures/verification-results'
import { servedFlavors } from './fixtures/flavors'

vi.mock('vscode', () => {
  return {}
})

vi.mock('../lib/session/monaco', () => {
  return {}
})

const MODEL_URI = 'file:///workspace/snippet.oxsts'

function caseAt(id: string, line: number): VerificationCaseSpecification {
  return {
    id,
    label: `Case ${id}`,
    location: {
      uri: MODEL_URI,
      range: { start: { line, character: 0 }, end: { line, character: 1 } },
    },
  }
}

interface RoutedClient {
  client: LspClient
  cases: VerificationCaseSpecification[]
  results: Map<number, () => Promise<VerificationCaseResult>>
  verifiedLines: number[]
}

// answers discovery with `cases` and a verify with the result registered for the case's line
function routedClient(cases: VerificationCaseSpecification[]): RoutedClient {
  const routed: RoutedClient = {
    cases,
    results: new Map(),
    verifiedLines: [],
    client: {
      sendRequest: async (method, parameters) => {
        if (method !== 'workspace/executeCommand') {
          return null
        }
        const { command, arguments: commandArguments } = parameters as { command: string, arguments: [{ range: { start: { line: number } } }] }
        if (command === 'oxsts.case.discover') {
          return routed.cases
        }
        if (command === 'oxsts.case.verify') {
          const line = commandArguments[0].range.start.line
          routed.verifiedLines.push(line)
          return (routed.results.get(line) ?? (async () => {
            return passedResult
          }))()
        }
        return null
      },
      sendNotification: () => {},
    },
  }
  return routed
}

function deferred<T>(): { promise: Promise<T>, resolve: (value: T) => void } {
  let resolve: (value: T) => void = () => {}
  const promise = new Promise<T>((settle) => {
    resolve = settle
  })
  return { promise, resolve }
}

function verificationOf(test: TestLive): VerificationState {
  const key = test.live.workspace.get().documentKey
  return key === null ? NO_VERIFICATION : test.live.verification.get().get(key) ?? NO_VERIFICATION
}

async function openConnected(routed: RoutedClient, search = ''): Promise<TestLive> {
  const test = createTestLive(routed.client)
  test.live.start(search)
  await vi.waitFor(() => {
    expect(test.live.workspace.get().runtime).not.toBeNull()
  })
  const record = test.live.documents.get().get(test.live.workspace.get().documentKey!)!
  test.models.open(record.uri, record.text)
  test.runtime().connect()
  await vi.waitFor(() => {
    expect(verificationOf(test).discovery).toBe('discovered')
  })
  return test
}

async function runAll(test: TestLive): Promise<void> {
  test.live.verifyAll()
  await vi.waitFor(() => {
    expect(verificationOf(test).phase).toBe('done')
  })
}

describe('Live', () => {
  let open: TestLive | null = null

  afterEach(async () => {
    await open?.live.dispose()
    open = null
    window.localStorage.clear()
  })

  it('keeps the edits and the verdicts of an example across a switch to another example and back, on one runtime', async () => {
    const routed = routedClient([caseAt('a', 1)])
    const [first, second] = testFlavors()[0].examples
    open = await openConnected(routed, `/oxsts/${first!.id}`)
    await runAll(open)
    open.models.edit(MODEL_URI, 'edited model')

    open.live.selectModel('oxsts', second!.id)
    expect(open.models.read(MODEL_URI)).toBe(second!.code)
    open.live.selectModel('oxsts', first!.id)

    expect(open.models.read(MODEL_URI)).toBe('edited model')
    expect(verificationOf(open).cases[0]).toMatchObject({ status: 'passed', outdated: true })
    expect(open.runtimes).toHaveLength(1)
  })

  it('keeps a result after an edit and marks it outdated', async () => {
    const routed = routedClient([caseAt('a', 1)])
    open = await openConnected(routed)
    await runAll(open)

    open.models.edit(MODEL_URI, 'edited model')

    expect(verificationOf(open).cases[0]).toMatchObject({ status: 'passed', resultStatus: 'passed', outdated: true })
  })

  it('keeps the earlier results of Verify all until the new ones arrive', async () => {
    const routed = routedClient([caseAt('a', 1)])
    routed.results.set(1, async () => {
      return failedResult
    })
    open = await openConnected(routed)
    await runAll(open)
    const pending = deferred<VerificationCaseResult>()
    routed.results.set(1, async () => {
      return pending.promise
    })

    open.live.verifyAll()
    await vi.waitFor(() => {
      expect(verificationOf(open!).cases[0]?.status).toBe('running')
    })
    expect(verificationOf(open).cases[0]).toMatchObject({ resultStatus: 'failed', trace: failedResult.trace })

    pending.resolve(passedResult)
    await vi.waitFor(() => {
      expect(verificationOf(open!).phase).toBe('done')
    })
    expect(verificationOf(open).cases[0]).toMatchObject({ status: 'passed', resultStatus: 'passed' })
    expect(verificationOf(open).cases[0]?.trace).toBeUndefined()
  })

  it('starts a Verify all pressed during initialization once the cases are known', async () => {
    const routed = routedClient([caseAt('a', 1)])
    open = createTestLive(routed.client)
    open.live.start('')
    await vi.waitFor(() => {
      expect(open!.live.workspace.get().runtime).not.toBeNull()
    })

    open.live.verifyAll()
    expect(routed.verifiedLines).toEqual([])
    open.models.open(MODEL_URI, 'model')
    open.runtime().connect()

    await vi.waitFor(() => {
      expect(verificationOf(open!).phase).toBe('done')
    })
    expect(routed.verifiedLines).toEqual([1])
    expect(open.live.initialization.get().steps.every((step) => {
      return step.state === 'done'
    })).toBe(true)
  })

  it('follows the model: a removed case goes with its result and a new one is not run', async () => {
    const routed = routedClient([caseAt('a', 1), caseAt('b', 2)])
    open = await openConnected(routed)
    await runAll(open)

    routed.cases = [caseAt('b', 2), caseAt('c', 3)]
    open.models.edit(MODEL_URI, 'edited model')

    await vi.waitFor(() => {
      expect(verificationOf(open!).cases.map((caseState) => {
        return caseState.caseInfo.id
      })).toEqual(['b', 'c'])
    })
    expect(verificationOf(open).cases.map((caseState) => {
      return caseState.status
    })).toEqual(['passed', 'stale'])
  })

  it('selects the first failed case with a trace after Verify all, and the case after a single run', async () => {
    const routed = routedClient([caseAt('a', 1), caseAt('b', 2)])
    routed.results.set(2, async () => {
      return failedResult
    })
    open = await openConnected(routed)

    await runAll(open)
    expect(open.live.selection.get()).toBe('b')

    open.live.verifyCase('a')
    await vi.waitFor(() => {
      expect(open!.live.selection.get()).toBe('a')
    })
  })

  it('closes a single run on its outcome, not on the case it verified', async () => {
    const routed = routedClient([caseAt('a', 1)])
    open = await openConnected(routed)

    open.live.verifyCase('a')

    await vi.waitFor(() => {
      expect(verificationOf(open!).phase).toBe('done')
    })
    expect(verificationOf(open).message).toBe('Case a passed')
  })

  it('verifies with the remembered portfolio, and with the default when the server does not offer it', async () => {
    const routed = routedClient([caseAt('a', 1)])
    open = await openConnected(routed)
    await vi.waitFor(() => {
      expect(open!.live.catalog.get().portfolios).not.toHaveLength(0)
    })

    open.live.settings.update((settings) => {
      return { ...settings, verificationPortfolioId: 'theta' }
    })
    expect(open.live.verificationPortfolioId()).toBe('theta')
    open.live.settings.update((settings) => {
      return { ...settings, verificationPortfolioId: 'retired' }
    })
    expect(open.live.verificationPortfolioId()).toBe('smart-full')
  })

  it('opens the default language on a bare address, and says which name of an address it did not find', async () => {
    const routed = routedClient([])
    const [, gamma] = [servedFlavors[0], servedFlavors[3]]
    open = createTestLive(routed.client, servedFlavors, { defaultFlavorId: gamma!.id })
    open.live.start('')
    await vi.waitFor(() => {
      expect(open!.live.workspace.get().flavor?.id).toBe(gamma!.id)
    })
    expect(open.live.address.get()).toBeNull()
    await open.live.dispose()

    open = createTestLive(routed.client, servedFlavors, { defaultFlavorId: gamma!.id })
    open.live.start('/kerml')
    await vi.waitFor(() => {
      expect(open!.live.address.get()).toMatchObject({ kind: 'language', requested: 'kerml', flavorId: gamma!.id })
    })
    await open.live.dispose()

    open = createTestLive(routed.client)
    open.live.start('/oxsts/missing')
    await vi.waitFor(() => {
      expect(open!.live.address.get()).toMatchObject({ kind: 'example', requested: 'missing', flavorId: 'oxsts' })
    })
    open.live.selectModel('oxsts', testFlavors()[0].examples[0]!.id)
    expect(open.live.address.get()).toBeNull()
  })

  it('cancels one case of Verify all and runs the rest', async () => {
    const routed = routedClient([caseAt('a', 1), caseAt('b', 2)])
    const pending = deferred<VerificationCaseResult>()
    routed.results.set(1, async () => {
      return pending.promise
    })
    open = await openConnected(routed)

    open.live.verifyAll()
    await vi.waitFor(() => {
      expect(verificationOf(open!).cases[0]?.status).toBe('running')
    })
    open.live.cancelCase('a')

    await vi.waitFor(() => {
      expect(verificationOf(open!).phase).toBe('done')
    })
    expect(verificationOf(open).cases.map((caseState) => {
      return [caseState.status, caseState.message]
    })).toEqual([['stale', 'Verification cancelled'], ['passed', undefined]])
    expect(open.live.runTiming.get()).toMatchObject({ caseId: null, finishedAt: expect.any(Number) })
    pending.resolve(failedResult)
  })

  it('shows Verify all as pending until the cases are known, and drops it on cancel', async () => {
    const routed = routedClient([caseAt('a', 1)])
    open = createTestLive(routed.client)
    open.live.start('')
    await vi.waitFor(() => {
      expect(open!.live.workspace.get().runtime).not.toBeNull()
    })

    open.live.verifyAll()
    expect(open.live.pendingVerifyAll.get()).toBe(true)
    open.live.cancelVerification()
    expect(open.live.pendingVerifyAll.get()).toBe(false)
  })

  it('starts a new session for the open document when asked after the server ended the last one', async () => {
    const routed = routedClient([caseAt('a', 1)])
    open = await openConnected(routed)
    const first = open.runtime()

    open.live.restartSession()

    expect(open.runtimes).toHaveLength(2)
    expect(open.runtime()).not.toBe(first)
    expect(open.live.workspace.get().runtime).toBe(open.runtime())
  })

  it('closes the read-only tabs with the language they belong to and keeps them on an example switch', async () => {
    const routed = routedClient([])
    open = await openConnected(routed)
    open.live.tabs.open({ id: 'library', label: 'semantifyr.oxsts', uri: 'live-library:/workspace/.semantifyr/builtin/semantifyr.oxsts', languageId: 'oxsts', banner: '' })

    open.live.selectModel('oxsts', testFlavors()[0].examples[1]!.id)
    expect(open.live.tabs.state.get().tabs).toHaveLength(1)
    open.live.selectModel('gamma', testFlavors()[3]!.examples[0]!.id)
    expect(open.live.tabs.state.get().tabs).toHaveLength(0)
  })
})
