/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { beforeAll, describe, expect, it } from 'vitest'

import { chooseShell, embedViewOf, sessionKindOf } from '../shells/route'
import { createShellState } from '../shells/shellState'

describe('the address and the width choose the shell', () => {
  it('opens the verifying embed on embed=1 and the edit-only one on embed=edit, whatever the width', () => {
    expect(embedViewOf('?mode=oxsts&embed=1')).toBe('verify')
    expect(embedViewOf('?embed=edit')).toBe('edit-only')
    expect(chooseShell('?embed=1', true)).toBe('embed')
    expect(chooseShell('?embed=edit', false)).toBe('embed')
  })

  it('opens the phone below the phone width and the desktop above it', () => {
    expect(chooseShell('?mode=gamma', true)).toBe('phone')
    expect(chooseShell('', false)).toBe('desktop')
    expect(chooseShell('?embed=0', false)).toBe('desktop')
  })

  it('names the session after the view, so the metrics tell them apart', () => {
    expect(sessionKindOf('?embed=1')).toBe('embed')
    expect(sessionKindOf('?embed=edit')).toBe('edit-only')
    expect(sessionKindOf('?example=crossroads')).toBe('full')
  })
})

describe('on touch input the model is read-only until the edit control is pressed', () => {
  // a phone: a coarse pointer and no fine one
  beforeAll(() => {
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      writable: true,
      value: (query: string) => {
        return {
          matches: query === '(pointer: coarse)',
          addEventListener: () => {},
          removeEventListener: () => {},
        }
      },
    })
  })

  it('locks the model until editing, and locks it again when done', async () => {
    const { modelLockOf } = await import('../shells/touch')
    const shell = createShellState()
    const lock = modelLockOf(shell)
    const unsubscribe = lock.subscribe(() => {})
    expect(lock.get()).toBe(true)
    shell.editing.set(true)
    expect(lock.get()).toBe(false)
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'a' }))
    shell.editing.set(false)
    expect(lock.get(), 'a key typed while editing comes from the on-screen keyboard').toBe(true)
    unsubscribe()
  })

  it('edits at once once a hardware keyboard or a mouse shows itself', async () => {
    const { modelLockOf, touchInput } = await import('../shells/touch')
    const shell = createShellState()
    const lock = modelLockOf(shell)
    const unsubscribe = lock.subscribe(() => {})
    expect(lock.get()).toBe(true)
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Unidentified' }))
    expect(lock.get(), 'an on-screen keyboard names no key').toBe(true)
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'a' }))
    expect(lock.get()).toBe(false)
    expect(touchInput().get()).toBe(false)
    unsubscribe()
  })
})
