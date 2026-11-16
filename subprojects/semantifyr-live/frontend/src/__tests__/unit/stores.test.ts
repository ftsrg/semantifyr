/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { afterEach, describe, expect, it, vi } from 'vitest'
import { createStore } from '../../lib/stores/store'
import { changeSettings, createSettingsStore, nextTheme } from '../../lib/stores/settings'
import {
  areCasesKnown,
  currentStep,
  failStep,
  finishStep,
  initializationOf,
  InitializationSteps,
  isInitialized,
  isReadyToVerify,
  startStep,
} from '../../lib/stores/initialization'

describe('createStore', () => {
  it('notifies a subscriber of a change and not of a set to the same value', () => {
    const store = createStore({ count: 0 })
    const listener = vi.fn()
    const unsubscribe = store.subscribe(listener)

    store.update((previous) => {
      return { count: previous.count + 1 }
    })
    store.set(store.get())
    unsubscribe()
    store.set({ count: 5 })

    expect(listener).toHaveBeenCalledTimes(1)
    expect(store.get()).toEqual({ count: 5 })
  })

  it('notifies every subscriber when one throws, and lets the error reach the page', () => {
    const store = createStore(0)
    const later = vi.fn()
    const failure = new Error('a listener failed')
    const queued: (() => void)[] = []
    const queue = vi.spyOn(globalThis, 'queueMicrotask').mockImplementation((callback) => {
      queued.push(callback)
    })
    store.subscribe(() => {
      throw failure
    })
    store.subscribe(later)

    store.set(1)
    queue.mockRestore()

    expect(later).toHaveBeenCalledTimes(1)
    expect(queued).toHaveLength(1)
    expect(queued[0]).toThrow(failure)
  })
})

describe('settings', () => {
  afterEach(() => {
    window.localStorage.clear()
  })

  it('remembers the display preferences in the browser, the verification portfolio among them', () => {
    const settings = createSettingsStore()
    changeSettings(settings, {
      theme: 'light',
      verificationPortfolioId: 'theta',
      validationPortfolioId: 'all-agree',
      autoValidate: false,
      resultsPanelOpen: false,
      sidePanelWidth: 512,
    })

    const reloaded = createSettingsStore().get()

    expect(reloaded).toMatchObject({
      theme: 'light',
      verificationPortfolioId: 'theta',
      validationPortfolioId: 'all-agree',
      autoValidate: false,
      resultsPanelOpen: false,
      sidePanelWidth: 512,
    })
  })

  it('remembers the pane sizes and the collapsed column', () => {
    changeSettings(createSettingsStore(), {
      resultsColumnWidth: 640,
      caseListHeight: 180,
      resultsColumnCollapsed: true,
    })

    expect(createSettingsStore().get()).toMatchObject({
      resultsColumnWidth: 640,
      caseListHeight: 180,
      resultsColumnCollapsed: true,
    })
  })

  it('starts from the defaults on a first visit', () => {
    expect(createSettingsStore().get()).toMatchObject({
      theme: 'system',
      verificationPortfolioId: null,
      validationPortfolioId: 'smart',
      autoValidate: true,
    })
  })

  it('cycles the theme through system, light and dark', () => {
    expect([nextTheme('system'), nextTheme('light'), nextTheme('dark')]).toEqual(['light', 'dark', 'system'])
  })
})

describe('initialization', () => {
  const steps = [
    { id: 'connect', label: 'Connecting' },
    { id: 'worker', label: 'Starting the worker' },
    { id: 'cases', label: 'Finding the cases' },
  ]

  it('is over only when every step is done, and names the step started last meanwhile', () => {
    let state = startStep(startStep(initializationOf(steps), 'connect', 1), 'worker', 2)
    expect(currentStep(state)?.id).toBe('worker')

    state = finishStep(finishStep(state, 'connect', 3), 'worker', 4)
    expect(isInitialized(state)).toBe(false)
    state = finishStep(state, 'cases', 5)

    expect(isInitialized(state)).toBe(true)
    expect(state.steps.map((step) => {
      return [step.startedAt, step.finishedAt]
    })).toEqual([[1, 3], [2, 4], [5, 5]])
  })

  it('names a failed step before the running ones', () => {
    const state = failStep(startStep(startStep(initializationOf(steps), 'connect', 1), 'worker', 2), 'connect', 3)

    expect(currentStep(state)?.id).toBe('connect')
  })

  it('knows the cases before the language server is initialized, and is ready to verify once it is or once its initialization failed', () => {
    const connected = finishStep(initializationOf([
      InitializationSteps.connect,
      InitializationSteps.initialize,
      InitializationSteps.cases,
    ]), InitializationSteps.connect.id, 1)
    const initializing = startStep(connected, InitializationSteps.initialize.id, 1)
    const casesKnown = finishStep(initializing, InitializationSteps.cases.id, 2)

    expect([areCasesKnown(initializing), isReadyToVerify(initializing)]).toEqual([false, false])
    expect([areCasesKnown(casesKnown), isReadyToVerify(casesKnown)]).toEqual([true, false])
    expect(isReadyToVerify(finishStep(casesKnown, InitializationSteps.initialize.id, 3))).toBe(true)
    expect(isReadyToVerify(failStep(casesKnown, InitializationSteps.initialize.id, 3, 'did not end'))).toBe(true)
    expect(isReadyToVerify(finishStep(finishStep(connected, InitializationSteps.initialize.id, 3), InitializationSteps.cases.id, 4))).toBe(true)
    expect(isReadyToVerify(failStep(finishStep(connected, InitializationSteps.initialize.id, 3), InitializationSteps.cases.id, 4))).toBe(false)
  })
})
