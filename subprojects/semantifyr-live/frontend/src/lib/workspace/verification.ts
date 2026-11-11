/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { LiveFlavor } from '../../examples'
import type { ActiveVerificationInfo } from '../api/types'
import type { LanguageRuntime } from '../language/types'
import type { ModelDocuments } from '../session/modelDocuments'
import { failStep, finishStep, InitializationSteps, isReadyToVerify } from '../stores/initialization'
import type { Settings } from '../stores/settings'
import { createStore, type ReadableStore } from '../stores/store'
import {
  applyServerReport,
  discoverVerificationCases,
  dispatchAutoValidation,
  markEdited,
  markQueued,
  mergeDiscoveredCases,
  runAllVerifications,
  unmarkQueued,
  verifySingleCase,
  witnessValidationLocation,
  type AutoValidateRequest,
  type LspClient,
  type RunVerificationHandle,
  type VerificationCaseState,
  type VerificationState,
} from '../verification/engine'

export const NO_VERIFICATION: VerificationState = {
  phase: 'idle',
  discovery: 'idle',
  cases: [],
}

export interface VerificationTarget {
  key: string
  uri: string
}

interface VerificationSubject {
  target: VerificationTarget
  runtime: LanguageRuntime
  flavor: LiveFlavor
}

export interface VerificationOptions {
  settings: ReadableStore<Settings>
  modelDocuments: ModelDocuments
  // the document, runtime and flavor verification acts on now
  current: () => VerificationSubject | null
  portfolioId: () => string | undefined
  discoveryDelayMs: number
  // the waits before asking a session again whose discovery failed; once they are spent, initialization fails with the reason
  discoveryRetryMs: readonly number[]
  // how long a discovery may go unanswered
  discoveryTimeoutMs: number
}

export interface Verification {
  // the verification state of every document of the visit, by document key
  readonly states: ReadableStore<ReadonlyMap<string, VerificationState>>
  // the selected case of the current document
  readonly selection: ReadableStore<string | null>
  // a verification asked for before the cases were known or the language server was initialized; it starts once both are
  readonly queued: ReadableStore<QueuedVerification | null>
  // the start and end of the last run of the current document, the end null while it runs
  readonly timing: ReadableStore<RunTiming | null>
  discover: () => Promise<void>
  startQueued: () => void
  scheduleDiscovery: () => void
  documentEdited: (key: string) => void
  verifyAll: () => void
  verifyCase: (caseId: string) => void
  cancel: () => void
  // the session went: the run ends as a cancelled one, a queued verification still waits
  sessionLost: () => void
  // the session's verifications the server holds, each queued or running
  serverReported: (active: readonly ActiveVerificationInfo[]) => void
  cancelCase: (caseId: string) => void
  validate: (caseId: string) => void
  select: (caseId: string | null) => void
  // the case a shared link selected stays selected when the run ends
  holdSelection: (caseId: string | null) => void
  forget: (key: string) => void
  dispose: () => void
}

export interface QueuedVerification {
  // the case a single run verifies, null for Verify all
  caseId: string | null
}

export interface RunTiming {
  key: string
  // the case a single run verifies, null for Verify all
  caseId: string | null
  startedAt: number
  finishedAt: number | null
}

type StateUpdater = (next: VerificationState | ((previous: VerificationState) => VerificationState)) => void

interface ActiveRun {
  key: string
  handle: RunVerificationHandle
  selectionChanged: boolean
}

interface Connected extends VerificationSubject {
  client: LspClient
}

function answeredWithin<T>(answer: Promise<T>, timeoutMs: number, message: string): Promise<T> {
  let timer: ReturnType<typeof setTimeout> | null = null
  const timeout = new Promise<never>((_resolve, reject) => {
    timer = setTimeout(() => {
      reject(new Error(message))
    }, timeoutMs)
  })
  return Promise.race([answer, timeout]).finally(() => {
    if (timer) {
      clearTimeout(timer)
    }
  })
}

function firstWithTrace(cases: readonly VerificationCaseState[]): VerificationCaseState | undefined {
  return cases.find((caseState) => {
    return caseState.status === 'failed' && caseState.trace !== undefined
  }) ?? cases.find((caseState) => {
    return caseState.trace !== undefined
  })
}

export function createVerification(options: VerificationOptions): Verification {
  const states = createStore<ReadonlyMap<string, VerificationState>>(new Map())
  const selection = createStore<string | null>(null)
  const queued = createStore<QueuedVerification | null>(null)
  const timing = createStore<RunTiming | null>(null)
  let run: ActiveRun | null = null
  let discoveryTimer: ReturnType<typeof setTimeout> | null = null
  let retryTimer: ReturnType<typeof setTimeout> | null = null
  let discoveryGeneration = 0
  let selectionHeld = false
  // when the verification held for the language server was asked for
  let heldSince: number | null = null

  const stateOf = (key: string): VerificationState => {
    return states.get().get(key) ?? NO_VERIFICATION
  }

  const updaterFor = (key: string): StateUpdater => {
    return (next) => {
      states.update((previousStates) => {
        const previous = previousStates.get(key) ?? NO_VERIFICATION
        const resolved = typeof next === 'function' ? next(previous) : next
        if (resolved === previous) {
          return previousStates
        }
        const changed = new Map(previousStates)
        changed.set(key, resolved)
        return changed
      })
    }
  }

  const connected = (): Connected | null => {
    const current = options.current()
    if (current?.runtime.connection.get().status !== 'connected') {
      return null
    }
    const client = current.runtime.client()
    if (!client) {
      return null
    }
    return { ...current, client }
  }

  const isReady = (current: VerificationSubject): boolean => {
    return current.runtime.connection.get().status === 'connected' && isReadyToVerify(current.runtime.initialization.get())
  }

  const isCurrent = (key: string): boolean => {
    return options.current()?.target.key === key
  }

  const autoValidateRequest = (flavor: LiveFlavor): AutoValidateRequest | null => {
    const { autoValidate, validationPortfolioId } = options.settings.get()
    if (!autoValidate || !flavor.validateWitnessCommand) {
      return null
    }
    return {
      command: flavor.validateWitnessCommand,
      byCase: flavor.validateWitnessByCase,
      portfolioId: validationPortfolioId,
    }
  }

  const queue = (caseId: string | null): void => {
    const askedAt = Date.now()
    heldSince = askedAt
    queued.set({ caseId })
    const key = options.current()?.target.key
    if (key !== undefined) {
      updaterFor(key)((state) => {
        return markQueued(state, caseId, askedAt)
      })
    }
  }

  const select = (caseId: string | null): void => {
    selectionHeld = false
    selection.set(caseId)
    if (run) {
      run.selectionChanged = true
    }
  }

  const startRun = (
    key: string,
    caseId: string | null,
    start: (connection: Connected, setState: StateUpdater) => RunVerificationHandle,
    whenDone: (handle: ActiveRun) => void,
  ): void => {
    const connection = connected()
    if (!connection || run || stateOf(key).phase === 'running') {
      return
    }
    const handle = start(connection, updaterFor(key))
    const active: ActiveRun = { key, handle, selectionChanged: false }
    run = active
    timing.set({ key, caseId, startedAt: heldSince ?? Date.now(), finishedAt: null })
    heldSince = null
    void handle.done.then(() => {
      if (run === active) {
        run = null
      }
      timing.update((previous) => {
        return previous?.key === key ? { ...previous, finishedAt: Date.now() } : previous
      })
      if (isCurrent(key)) {
        whenDone(active)
      }
    })
  }

  const verifyAll = (): void => {
    const current = options.current()
    if (!current || !isReady(current)) {
      queue(null)
      return
    }
    const { key, uri } = current.target
    const cases = stateOf(key).cases.map((caseState) => {
      return caseState.caseInfo
    })
    if (cases.length === 0) {
      updaterFor(key)((previous) => {
        return { phase: 'error', discovery: previous.discovery, message: 'No verification cases found.', cases: [] }
      })
      return
    }
    startRun(key, null, (connection, setState) => {
      return runAllVerifications(
        connection.client,
        connection.flavor.verificationCommand,
        uri,
        cases,
        setState,
        connection.runtime.addProgressListener,
        {
          portfolioId: options.portfolioId(),
          getAutoValidateRequest: () => {
            return autoValidateRequest(connection.flavor)
          },
        },
      )
    }, (active) => {
      const state = stateOf(key)
      if (active.selectionChanged || selectionHeld || state.phase !== 'done') {
        return
      }
      const shown = firstWithTrace(state.cases)
      if (shown) {
        selection.set(shown.caseInfo.id)
      }
    })
  }

  const verifyCase = (caseId: string): void => {
    const current = options.current()
    if (!current) {
      return
    }
    const { key, uri } = current.target
    const target = stateOf(key).cases.find((caseState) => {
      return caseState.caseInfo.id === caseId
    })
    if (!target) {
      return
    }
    if (!isReady(current)) {
      queue(caseId)
      return
    }
    startRun(key, caseId, (connection, setState) => {
      return verifySingleCase(
        connection.client,
        connection.flavor.verificationCommand,
        uri,
        target.caseInfo,
        setState,
        connection.runtime.addProgressListener,
        {
          portfolioId: options.portfolioId(),
          getAutoValidateRequest: () => {
            return autoValidateRequest(connection.flavor)
          },
        },
      )
    }, () => {
      if (stateOf(key).phase === 'done') {
        selection.set(caseId)
      }
    })
  }

  const clearRetryTimer = (): void => {
    if (retryTimer) {
      clearTimeout(retryTimer)
      retryTimer = null
    }
  }

  // the session may not have the document yet, or may not answer; it is asked again, and initialization fails with the reason once the waits are spent
  const discover = async (attempt = 0): Promise<void> => {
    clearRetryTimer()
    const connection = connected()
    if (!connection || !options.modelDocuments.isOpen(connection.target.uri)) {
      return
    }
    const { key, uri } = connection.target
    const generation = ++discoveryGeneration
    const setState = updaterFor(key)
    setState((previous) => {
      return { ...previous, discovery: 'discovering' }
    })
    try {
      const discovered = await answeredWithin(
        discoverVerificationCases(connection.client, connection.flavor.discoveryCommand, uri),
        options.discoveryTimeoutMs,
        `The server did not list the verification cases within ${Math.round(options.discoveryTimeoutMs / 1000)} s.`,
      )
      if (generation !== discoveryGeneration) {
        return
      }
      setState((previous) => {
        if (previous.phase === 'running') {
          return { ...previous, discovery: 'discovered' }
        }
        const merged = { ...previous, discovery: 'discovered' as const, cases: mergeDiscoveredCases(previous.cases, discovered) }
        return queued.get()?.caseId === null ? markQueued(merged, null, heldSince ?? Date.now()) : merged
      })
    } catch (error) {
      if (generation !== discoveryGeneration) {
        return
      }
      setState((previous) => {
        return { ...previous, discovery: 'idle' }
      })
      const current = options.current()
      if (current?.runtime !== connection.runtime || current.target.key !== key) {
        return
      }
      const wait = options.discoveryRetryMs[attempt]
      if (wait !== undefined) {
        retryTimer = setTimeout(() => {
          retryTimer = null
          void discover(attempt + 1)
        }, wait)
        return
      }
      const reason = error instanceof Error && error.message !== '' ? error.message : 'The server did not answer.'
      connection.runtime.initialization.update((state) => {
        return failStep(state, InitializationSteps.cases.id, Date.now(), `The verification cases could not be listed. ${reason}`)
      })
      return
    }
    connection.runtime.initialization.update((state) => {
      return finishStep(state, InitializationSteps.cases.id, Date.now())
    })
  }

  const startQueued = (): void => {
    const request = queued.get()
    const current = options.current()
    if (!request || !current || !isReady(current)) {
      return
    }
    queued.set(null)
    if (request.caseId === null) {
      verifyAll()
    } else {
      verifyCase(request.caseId)
    }
  }

  const clearDiscoveryTimer = (): void => {
    if (discoveryTimer) {
      clearTimeout(discoveryTimer)
      discoveryTimer = null
    }
  }

  const endRun = (): void => {
    const active = run
    run = null
    active?.handle.cancel()
  }

  const cancel = (): void => {
    const key = options.current()?.target.key
    if (queued.get() !== null && key !== undefined) {
      updaterFor(key)(unmarkQueued)
    }
    queued.set(null)
    heldSince = null
    endRun()
  }

  return {
    states,
    selection,
    queued,
    timing,
    discover,
    startQueued,
    scheduleDiscovery: () => {
      clearDiscoveryTimer()
      clearRetryTimer()
      discoveryTimer = setTimeout(() => {
        discoveryTimer = null
        void discover()
      }, options.discoveryDelayMs)
    },
    documentEdited: (key) => {
      updaterFor(key)(markEdited)
    },
    verifyAll,
    verifyCase,
    cancel,
    sessionLost: endRun,
    serverReported: (active) => {
      const key = options.current()?.target.key
      if (key !== undefined) {
        updaterFor(key)((state) => {
          return applyServerReport(state, active, Date.now())
        })
      }
    },
    cancelCase: (caseId) => {
      run?.handle.cancelCase(caseId)
    },
    validate: (caseId) => {
      const connection = connected()
      const command = connection?.flavor.validateWitnessCommand
      if (!connection || !command) {
        return
      }
      const { key } = connection.target
      const target = stateOf(key).cases.find((caseState) => {
        return caseState.caseInfo.id === caseId
      })
      const witnessUri = target?.trace?.witnessUri
      if (!target || !witnessUri) {
        return
      }
      const location = witnessValidationLocation(target.caseInfo.location, witnessUri, connection.flavor.validateWitnessByCase)
      dispatchAutoValidation(connection.client, command, caseId, location, updaterFor(key), {
        validationPortfolioId: options.settings.get().validationPortfolioId,
      })
    },
    select,
    holdSelection: (caseId) => {
      selection.set(caseId)
      selectionHeld = caseId !== null
    },
    forget: (key) => {
      states.update((previous) => {
        const changed = new Map(previous)
        changed.delete(key)
        return changed
      })
    },
    dispose: () => {
      clearDiscoveryTimer()
      clearRetryTimer()
      cancel()
    },
  }
}
