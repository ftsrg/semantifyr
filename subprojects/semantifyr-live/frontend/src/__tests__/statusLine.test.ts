/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from 'vitest'
import { connectionConnected, INITIAL_CONNECTION } from '../lib/stores/connection'
import { finishStep, initializationOf, startStep } from '../lib/stores/initialization'
import { statusLine } from '../lib/workspace/statusLine'
import { NO_VERIFICATION } from '../lib/workspace/verification'

const connected = connectionConnected(INITIAL_CONNECTION, 0)
const loadingLibraries = startStep(
  initializationOf([{ id: 'cases', label: 'Loading the libraries and finding the cases' }]),
  'cases',
  0,
)

describe('statusLine', () => {
  it('names the initialization step while a connected language is still loading, not Ready', () => {
    expect(statusLine(NO_VERIFICATION, connected, loadingLibraries)).toEqual({
      message: 'Loading the libraries and finding the cases...',
      busy: true,
    })
  })

  it('is Ready once the initialization is over', () => {
    expect(statusLine(NO_VERIFICATION, connected, finishStep(loadingLibraries, 'cases', 1))).toEqual({
      message: 'Ready',
      busy: false,
    })
  })

  it('gives the reason of a failed connection', () => {
    const failed = { ...INITIAL_CONNECTION, status: 'failed' as const, reason: 'Session limit reached' }

    expect(statusLine(NO_VERIFICATION, failed, loadingLibraries).message).toBe('Session limit reached')
  })
})
