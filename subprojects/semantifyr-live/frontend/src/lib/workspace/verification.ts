/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { LiveFlavor } from '../../examples'
import type { LanguageRuntime } from '../language/types'
import type { ModelDocuments } from '../session/modelDocuments'
import { finishStep, InitializationSteps, isInitialized } from '../stores/initialization'
import type { Settings } from '../stores/settings'
import { createStore, type ReadableStore } from '../stores/store'
import {
  discoverVerificationCases,
  dispatchAutoValidation,
  markEdited,
  mergeDiscoveredCases,
  runAllVerifications,
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

export interface VerificationOptions {
  settings: ReadableStore<Settings>
  modelDocuments: ModelDocuments
  // the document, runtime and flavor verification acts on now
  current: () => { target: VerificationTarget, runtime: LanguageRuntime, flavor: LiveFlavor } | null
  portfolioId: () => string | undefined
  discoveryDelayMs: number
}

export interface Verification {
  // the verification state of every document of the visit, by document key
  readonly states: ReadableStore<ReadonlyMap<string, VerificationState>>
  // the selected case of the current document
  readonly selection: ReadableStore<string | null>
  discover: () => Promise<void>
  scheduleDiscovery: () => void
  documentEdited: (key: string) => void
  verifyAll: () => void
  verifyCase: (caseId: string) => void
  cancel: () => void
  validate: (caseId: string) => void
  select: (caseId: string | null) => void
  dispose: () => void
}

type StateUpdater = (next: VerificationState | ((previous: VerificationState) => VerificationState)) => void

interface ActiveRun {
  key: string
  handle: RunVerificationHandle
  selectionChanged: boolean
}

interface Connected {
  target: VerificationTarget
  runtime: LanguageRuntime
  flavor: LiveFlavor
  client: LspClient
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
  let run: ActiveRun | null = null
  let pendingVerifyAll = false
  let discoveryTimer: ReturnType<typeof setTimeout> | null = null
  let discoveryGeneration = 0

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

  const select = (caseId: string | null): void => {
    selection.set(caseId)
    if (run) {
      run.selectionChanged = true
    }
  }

  const startRun = (
    key: string,
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
    void handle.done.then(() => {
      if (run === active) {
        run = null
      }
      if (isCurrent(key)) {
        whenDone(active)
      }
    })
  }

  const verifyAll = (): void => {
    const current = options.current()
    if (!current) {
      return
    }
    if (!isInitialized(current.runtime.initialization.get())) {
      pendingVerifyAll = true
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
    startRun(key, (connection, setState) => {
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
      if (active.selectionChanged || state.phase !== 'done') {
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
    startRun(key, (connection, setState) => {
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

  const discover = async (): Promise<void> => {
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
      const discovered = await discoverVerificationCases(connection.client, connection.flavor.discoveryCommand, uri)
      if (generation !== discoveryGeneration) {
        return
      }
      setState((previous) => {
        if (previous.phase === 'running') {
          return { ...previous, discovery: 'discovered' }
        }
        return { ...previous, discovery: 'discovered', cases: mergeDiscoveredCases(previous.cases, discovered) }
      })
    } catch {
      if (generation === discoveryGeneration) {
        setState((previous) => {
          return { ...previous, discovery: 'idle' }
        })
      }
      return
    }
    connection.runtime.initialization.update((state) => {
      return finishStep(state, InitializationSteps.cases.id, Date.now())
    })
    if (pendingVerifyAll && isCurrent(key)) {
      pendingVerifyAll = false
      verifyAll()
    }
  }

  const clearDiscoveryTimer = (): void => {
    if (discoveryTimer) {
      clearTimeout(discoveryTimer)
      discoveryTimer = null
    }
  }

  const cancel = (): void => {
    pendingVerifyAll = false
    const active = run
    run = null
    active?.handle.cancel()
  }

  return {
    states,
    selection,
    discover,
    scheduleDiscovery: () => {
      clearDiscoveryTimer()
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
    dispose: () => {
      clearDiscoveryTimer()
      cancel()
    },
  }
}
