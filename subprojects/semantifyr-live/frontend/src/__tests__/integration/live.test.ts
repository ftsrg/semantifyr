/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { afterEach, describe, expect, it, vi } from 'vitest'
import type { VerificationCaseResult, VerificationCaseSpecification } from '@semantifyr/editor-common'
import type { ActiveVerificationInfo } from '../../lib/api/types'
import { areCasesKnown } from '../../lib/stores/initialization'
import type { LspClient, VerificationState } from '../../lib/verification/engine'
import { caseRowOf } from '../../lib/workspace/caseRows'
import { NO_VERIFICATION } from '../../lib/workspace/verification'
import { createTestLive, testFlavors, type TestLive } from '../helpers/live'
import { failedResult, passedResult } from '../fixtures/verification-results'
import { servedFlavors } from '../fixtures/flavors'

vi.mock('vscode', () => {
  return {}
})

vi.mock('../../lib/session/monaco', () => {
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
  portfolios: (string | undefined)[]
}

// answers discovery with `cases` and a verify with the result registered for the case's line
function routedClient(cases: VerificationCaseSpecification[]): RoutedClient {
  const routed: RoutedClient = {
    cases,
    results: new Map(),
    verifiedLines: [],
    portfolios: [],
    client: {
      sendRequest: async (method, parameters) => {
        if (method !== 'workspace/executeCommand') {
          return null
        }
        const { command, arguments: commandArguments } = parameters as { command: string, arguments: [{ range: { start: { line: number } }, portfolio?: string }] }
        if (command === 'oxsts.case.discover') {
          return routed.cases
        }
        if (command === 'oxsts.case.verify') {
          const line = commandArguments[0].range.start.line
          routed.verifiedLines.push(line)
          routed.portfolios.push(commandArguments[0].portfolio)
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

function reported(caseInfo: VerificationCaseSpecification, state: ActiveVerificationInfo['state']): ActiveVerificationInfo {
  return { verificationId: `verify-${caseInfo.id}`, portfolioId: 'smart-full', kind: 'Verify', state, elapsed: 'PT0S', location: caseInfo.location }
}

function verificationOf(test: TestLive): VerificationState {
  const key = test.live.workspace.get().documentKey
  return key === null ? NO_VERIFICATION : test.live.verification.get().get(key) ?? NO_VERIFICATION
}

async function openConnected(routed: RoutedClient, search = '', initializing = false): Promise<TestLive> {
  const test = createTestLive(routed.client, undefined, { initializing })
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

  it('shows the document it names after a switch to a flavour whose model has the same address, and keeps the edits of the one it left', async () => {
    const routed = routedClient([caseAt('a', 1)])
    const [plain, withGamma] = testFlavors()
    const first = plain.examples[0]!
    open = await openConnected(routed, `/oxsts/${first.id}`)
    open.models.edit(MODEL_URI, 'edited model')

    open.live.selectModel(withGamma!.id, withGamma!.examples[0]!.id)
    expect(open.live.currentText()).toBe(withGamma!.examples[0]!.code)
    expect(open.live.unsaved.get()).toBe(false)

    open.live.selectModel(plain.id, plain.examples[1]!.id)
    expect(open.live.currentText()).toBe(plain.examples[1]!.code)
    expect(open.live.unsaved.get()).toBe(false)

    open.live.selectModel(plain.id, first.id)
    expect(open.live.currentText()).toBe('edited model')
    expect(open.live.unsaved.get()).toBe(true)
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

  it('ends a run when its session goes: a case without a verdict is not run and cancelled, one with a verdict keeps it', async () => {
    const routed = routedClient([caseAt('a', 1), caseAt('b', 2), caseAt('c', 3)])
    open = await openConnected(routed)
    open.live.verifyCase('c')
    await vi.waitFor(() => {
      expect(verificationOf(open!).phase).toBe('done')
    })
    routed.results.set(1, async () => {
      return failedResult
    })
    const never = deferred<VerificationCaseResult>()
    routed.results.set(2, async () => {
      return never.promise
    })
    open.live.verifyAll()
    await vi.waitFor(() => {
      expect(verificationOf(open!).cases[1]?.status).toBe('running')
    })

    open.runtime().terminate()

    await vi.waitFor(() => {
      expect(open!.live.runTiming.get()?.finishedAt).not.toBeNull()
    })
    expect(verificationOf(open).phase).toBe('cancelled')
    expect(verificationOf(open).cases.map((caseState) => {
      return [caseState.status, caseState.message]
    })).toEqual([['failed', failedResult.message], ['stale', 'Verification cancelled'], ['passed', undefined]])
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

  it('starts no session before the portfolios are known, so a Verify all pressed early names one', async () => {
    const routed = routedClient([caseAt('a', 1)])
    let refusals = 1
    open = createTestLive(routed.client, servedFlavors, {
      catalogRetryMs: 0,
      server: {
        fetchFlavors: async () => {
          return [...servedFlavors]
        },
        fetchPortfolios: async () => {
          if (refusals > 0) {
            refusals--
            throw new Error('the server is not answering')
          }
          return { portfolios: [{ id: 'smart-full', displayName: 'Smart full', description: '', available: true }], defaultPortfolio: 'smart-full' }
        },
      },
    })
    open.live.start('')
    open.live.verifyAll()
    await vi.waitFor(() => {
      expect(open!.live.workspace.get().runtime).not.toBeNull()
    })
    open.models.open(MODEL_URI, 'model')
    open.runtime().connect()

    await vi.waitFor(() => {
      expect(verificationOf(open!).phase).toBe('done')
    })
    expect(routed.portfolios).toEqual(['smart-full'])
  })

  it('opens the address the visitor moved to while the server did not answer', async () => {
    const routed = routedClient([caseAt('a', 1)])
    let answering = false
    open = createTestLive(routed.client, servedFlavors, {
      catalogRetryMs: 0,
      server: {
        fetchFlavors: async () => {
          if (!answering) {
            throw new Error('the server is not answering')
          }
          return [...servedFlavors]
        },
        fetchPortfolios: async () => {
          return { portfolios: [], defaultPortfolio: 'smart-full' }
        },
      },
    })
    const gamma = testFlavors()[3]!
    open.live.start('/oxsts')
    await vi.waitFor(() => {
      expect(open!.live.catalog.get().status).toBe('waiting')
    })

    open.live.openAddress(`/gamma/${gamma.examples[1]!.id}`)
    answering = true

    await vi.waitFor(() => {
      expect(open!.live.workspace.get().documentKey).toBe(`gamma#${gamma.examples[1]!.id}`)
    })
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

  it('shows a sent case queued while the server queues it and running once the server runs it, and counts its time from then', async () => {
    const target = caseAt('a', 1)
    const routed = routedClient([target])
    open = await openConnected(routed)
    const pending = deferred<VerificationCaseResult>()
    routed.results.set(1, async () => {
      return pending.promise
    })
    const clock = vi.spyOn(Date, 'now').mockReturnValue(1_000)
    const row = (): ReturnType<typeof caseRowOf> => {
      return caseRowOf(verificationOf(open!).cases[0]!)
    }

    open.live.verifyCase('a')
    await vi.waitFor(() => {
      expect(routed.verifiedLines).toEqual([1])
    })
    expect(row().status).toBe('queued')
    open.runtime().report([reported(target, 'Queued')])
    expect(row().status).toBe('queued')
    clock.mockReturnValue(5_000)
    open.runtime().report([reported(target, 'Running')])
    expect(row().status).toBe('running')
    open.runtime().report([])
    expect(row().status).toBe('running')
    clock.mockReturnValue(7_500)
    pending.resolve({ ...passedResult, status: 'timeout', message: 'Verification timed out', metrics: null })

    await vi.waitFor(() => {
      expect(verificationOf(open!).phase).toBe('done')
    })
    expect(row()).toMatchObject({ status: 'inconclusive', time: '2.5 s' })
    clock.mockRestore()
  })

  it('shows every case asked for as queued while the language server initializes, the ones found meanwhile included, and not run again on cancel', async () => {
    const routed = routedClient([caseAt('a', 1), caseAt('b', 2)])
    open = createTestLive(routed.client, undefined, { initializing: true })
    open.live.start('')
    await vi.waitFor(() => {
      expect(open!.live.workspace.get().runtime).not.toBeNull()
    })

    open.live.verifyAll()
    open.runtime().connect()
    open.models.open(MODEL_URI, 'model')
    await vi.waitFor(() => {
      expect(verificationOf(open!).discovery).toBe('discovered')
    })
    expect(verificationOf(open).cases.map(caseRowOf).map((row) => {
      return row.word
    })).toEqual(['Queued', 'Queued'])

    open.live.cancelVerification()
    expect(verificationOf(open).cases.map(caseRowOf).map((row) => {
      return [row.word, row.message]
    })).toEqual([['Not run', 'cancelled'], ['Not run', 'cancelled']])
    expect(routed.verifiedLines).toEqual([])
  })

  it('queues Verify all until the cases are known, and drops it on cancel', async () => {
    const routed = routedClient([caseAt('a', 1)])
    open = createTestLive(routed.client)
    open.live.start('')
    await vi.waitFor(() => {
      expect(open!.live.workspace.get().runtime).not.toBeNull()
    })

    open.live.verifyAll()
    expect(open.live.queuedVerification.get()).toEqual({ caseId: null })
    open.live.cancelVerification()
    expect(open.live.queuedVerification.get()).toBeNull()
  })

  it('lists the cases while the language server initializes, and starts a case asked for meanwhile once it has', async () => {
    const routed = routedClient([caseAt('a', 1), caseAt('b', 2)])
    open = await openConnected(routed, '', true)
    expect(areCasesKnown(open.live.initialization.get())).toBe(true)

    open.live.verifyCase('b')
    expect(open.live.queuedVerification.get()).toEqual({ caseId: 'b' })
    expect(verificationOf(open).cases.map((caseState) => {
      return caseState.status
    })).toEqual(['stale', 'queued'])
    expect(routed.verifiedLines).toEqual([])
    open.runtime().initialized()

    await vi.waitFor(() => {
      expect(verificationOf(open!).phase).toBe('done')
    })
    expect(routed.verifiedLines).toEqual([2])
    expect(open.live.queuedVerification.get()).toBeNull()
  })

  it('starts a Verify all asked for before anything was known once the cases arrive after the language server initialized', async () => {
    const routed = routedClient([caseAt('a', 1)])
    open = createTestLive(routed.client, undefined, { initializing: true })
    open.live.start('')
    await vi.waitFor(() => {
      expect(open!.live.workspace.get().runtime).not.toBeNull()
    })

    open.live.verifyAll()
    open.runtime().connect()
    open.runtime().initialized()
    expect(areCasesKnown(open.live.initialization.get())).toBe(false)
    expect(routed.verifiedLines).toEqual([])
    open.models.open(MODEL_URI, 'model')

    await vi.waitFor(() => {
      expect(verificationOf(open!).phase).toBe('done')
    })
    expect(routed.verifiedLines).toEqual([1])
  })

  it('keeps only the verification asked for last while the language server initializes', async () => {
    const routed = routedClient([caseAt('a', 1), caseAt('b', 2)])
    open = await openConnected(routed, '', true)

    open.live.verifyAll()
    open.live.verifyCase('a')
    expect(open.live.queuedVerification.get()).toEqual({ caseId: 'a' })
    open.runtime().initialized()

    await vi.waitFor(() => {
      expect(verificationOf(open!).phase).toBe('done')
    })
    expect(routed.verifiedLines).toEqual([1])
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
