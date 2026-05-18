/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import {
  persistBool,
  persistSize,
  persistString,
  readPersistedBool,
  readPersistedSize,
  readPersistedString,
} from '../lib/util/persistence'

const KEY = 'persistence-test-key'

describe('persistence helpers', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })
  afterEach(() => {
    window.localStorage.clear()
  })

  it('readPersistedSize falls back when nothing is stored', () => {
    expect(readPersistedSize(KEY, 480)).toBe(480)
  })

  it('readPersistedSize falls back when stored value is not a positive integer', () => {
    window.localStorage.setItem(KEY, 'not-a-number')
    expect(readPersistedSize(KEY, 480)).toBe(480)
    window.localStorage.setItem(KEY, '0')
    expect(readPersistedSize(KEY, 480)).toBe(480)
    window.localStorage.setItem(KEY, '-12')
    expect(readPersistedSize(KEY, 480)).toBe(480)
  })

  it('readPersistedSize round-trips through persistSize and rounds floats', () => {
    persistSize(KEY, 321.7)
    expect(window.localStorage.getItem(KEY)).toBe('322')
    expect(readPersistedSize(KEY, 0)).toBe(322)
  })

  it('readPersistedString returns the fallback when missing', () => {
    expect(readPersistedString(KEY, 'fallback')).toBe('fallback')
    persistString(KEY, 'stored')
    expect(readPersistedString(KEY, 'fallback')).toBe('stored')
  })

  it('readPersistedBool only honours exact "true"/"false" strings', () => {
    expect(readPersistedBool(KEY, true)).toBe(true)
    persistBool(KEY, false)
    expect(readPersistedBool(KEY, true)).toBe(false)
    window.localStorage.setItem(KEY, 'yes')
    expect(readPersistedBool(KEY, true)).toBe(true)
    window.localStorage.setItem(KEY, '')
    expect(readPersistedBool(KEY, false)).toBe(false)
  })
})
