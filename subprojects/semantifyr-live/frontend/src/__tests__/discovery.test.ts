/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { afterEach, describe, expect, it, vi } from 'vitest'
import type { VerificationCaseSpecification } from '@semantifyr/editor-common'
import { finishStep, initializationOf, InitializationSteps, isInitialized, startStep, type InitializationState } from '../lib/stores/initialization'
import type { LspClient } from '../lib/verification/engine'
import { createTestLive, type TestLive } from './helpers/live'

// Goal: a session's initialization always ends with the cases listed or with the reason they are not, whatever the session answers to the discovery

vi.mock('vscode', () => {
  return {}
})

vi.mock('../lib/session/monaco', () => {
  return {}
})

const CASES: VerificationCaseSpecification[] = [
  {
    id: 'a',
    label: 'Case a',
    location: {
      uri: 'file:///workspace/snippet.oxsts',
      range: { start: { line: 1, character: 0 }, end: { line: 1, character: 1 } },
    },
  },
]

// answers a discovery as `answer` says, counting the discoveries asked
function discoveryClient(answer: (attempt: number) => Promise<unknown>): { client: LspClient, attempts: () => number } {
  let attempts = 0
  return {
    client: {
      sendRequest: async (method) => {
        if (method !== 'workspace/executeCommand') {
          return null
        }
        attempts += 1
        return answer(attempts)
      },
      sendNotification: () => {},
    },
    attempts: () => {
      return attempts
    },
  }
}

function casesStep(state: InitializationState): InitializationState['steps'][number] | undefined {
  return state.steps.find((step) => {
    return step.id === InitializationSteps.cases.id
  })
}

async function openConnected(client: LspClient, retries: readonly number[], timeoutMs = 1000): Promise<TestLive> {
  const test = createTestLive(client, undefined, { discoveryRetryMs: retries, discoveryTimeoutMs: timeoutMs })
  test.live.start('/oxsts')
  await vi.waitFor(() => {
    expect(test.live.workspace.get().runtime).not.toBeNull()
  })
  const record = test.live.documents.get().get(test.live.workspace.get().documentKey!)!
  test.models.open(record.uri, record.text)
  test.runtime().connect()
  return test
}

describe('finding the verification cases', () => {
  let open: TestLive | null = null

  afterEach(async () => {
    await open?.live.dispose()
    open = null
  })

  it('asks again a session that did not have the document yet, and lists the cases', async () => {
    const session = discoveryClient(async (attempt) => {
      if (attempt === 1) {
        throw new Error('No resource registered for uri=file:///workspace/snippet.oxsts')
      }
      return CASES
    })
    open = await openConnected(session.client, [10])

    await vi.waitFor(() => {
      expect(isInitialized(open!.live.initialization.get())).toBe(true)
    })
    expect(session.attempts()).toBeGreaterThanOrEqual(2)
    expect(open.live.verification.get().get(open.live.workspace.get().documentKey!)?.cases).toHaveLength(1)
  })

  it('ends initialization with the reason once a session keeps refusing', async () => {
    const session = discoveryClient(async () => {
      throw new Error('No resource registered for uri=file:///workspace/snippet.oxsts')
    })
    open = await openConnected(session.client, [10, 10])

    await vi.waitFor(() => {
      expect(casesStep(open!.live.initialization.get())?.state).toBe('failed')
    })
    expect(session.attempts()).toBeGreaterThanOrEqual(3)
    expect(casesStep(open.live.initialization.get())?.reason).toBe('The verification cases could not be listed. No resource registered for uri=file:///workspace/snippet.oxsts')
  })

  it('ends initialization with what it waited for when a session never answers', async () => {
    const session = discoveryClient(() => {
      return new Promise(() => {})
    })
    open = await openConnected(session.client, [], 50)

    await vi.waitFor(() => {
      expect(casesStep(open!.live.initialization.get())?.state).toBe('failed')
    })
    expect(casesStep(open.live.initialization.get())?.reason).toBe('The verification cases could not be listed. The server did not list the verification cases within 0 s.')
  })

  it('lists the cases after a failure once the model changes', async () => {
    let refusing = true
    const session = discoveryClient(async () => {
      if (refusing) {
        throw new Error('refused')
      }
      return CASES
    })
    open = await openConnected(session.client, [])
    await vi.waitFor(() => {
      expect(casesStep(open!.live.initialization.get())?.state).toBe('failed')
    })

    refusing = false
    open.models.edit('file:///workspace/snippet.oxsts', 'edited model')

    await vi.waitFor(() => {
      expect(isInitialized(open!.live.initialization.get())).toBe(true)
    })
  })

  it('asks for the cases when the session has loaded the model, though the discovery at the connect found nothing to ask', async () => {
    let documentKnown = false
    const session = discoveryClient(async () => {
      if (!documentKnown) {
        throw new Error('No resource registered for uri=file:///workspace/snippet.oxsts')
      }
      return CASES
    })
    open = await openConnected(session.client, [60_000])
    await vi.waitFor(() => {
      expect(open!.live.verification.get().get(open!.live.workspace.get().documentKey!)?.discovery).toBe('idle')
    })
    const asked = session.attempts()
    const runtime = open.runtime()
    runtime.initialization.update((state) => {
      return { steps: state.steps.map((step) => {
        return step.id === InitializationSteps.cases.id ? { id: step.id, label: step.label, state: 'pending', startedAt: null, finishedAt: null } : step
      }) }
    })

    documentKnown = true
    runtime.initialization.update((state) => {
      return startStep(state, InitializationSteps.cases.id, Date.now())
    })

    await vi.waitFor(() => {
      expect(isInitialized(open!.live.initialization.get())).toBe(true)
    })
    expect(session.attempts()).toBe(asked + 1)
  })

  it('ends the library step with what it waited for when the diagnostics of the model never come, and lists the cases once the model is there', async () => {
    const session = discoveryClient(async () => {
      return CASES
    })
    open = createTestLive(session.client, undefined, { libraryTimeoutMs: 50 })
    open.live.start('/oxsts')
    await vi.waitFor(() => {
      expect(open!.live.workspace.get().runtime).not.toBeNull()
    })
    const runtime = open.runtime()
    runtime.connect()
    runtime.initialization.set(startStep(finishStep(initializationOf([
      InitializationSteps.connect,
      InitializationSteps.library,
      InitializationSteps.cases,
    ]), InitializationSteps.connect.id, Date.now()), InitializationSteps.library.id, Date.now()))

    await vi.waitFor(() => {
      expect(open!.live.initialization.get().steps.find((step) => {
        return step.id === InitializationSteps.library.id
      })?.reason).toBe('Loading the library did not end within 0 s.')
    })

    const record = open.live.documents.get().get(open.live.workspace.get().documentKey!)!
    open.models.open(record.uri, record.text)
    await vi.waitFor(() => {
      expect(isInitialized(open!.live.initialization.get())).toBe(true)
    })
  })
})
