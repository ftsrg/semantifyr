/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { afterEach, describe, expect, it, vi } from 'vitest'
import { createStore } from '../lib/stores/store'
import { changeSettings, createSettingsStore, nextTheme } from '../lib/stores/settings'
import {
  currentStep,
  failStep,
  finishStep,
  initializationOf,
  isInitialized,
  startStep,
} from '../lib/stores/initialization'

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

  it('starts from the defaults on a first visit', () => {
    expect(createSettingsStore().get()).toMatchObject({
      theme: 'system',
      verificationPortfolioId: null,
      validationPortfolioId: 'smart-full',
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
})
