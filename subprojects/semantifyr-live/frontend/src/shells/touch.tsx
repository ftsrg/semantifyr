/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'

import { createStore, type ReadableStore } from '../lib/stores/store'
import { useStore } from '../lib/stores/useStore'
import { Icon } from '../ui/icons'
import { useShell } from './shellContext'
import type { ShellState } from './shellState'

import './touch.css'

// the page's input is touch alone until a mouse or a hardware keyboard shows itself
const touchOnly = createStore(false)
let watching = false

function watchInput(): void {
  if (watching || typeof window === 'undefined') {
    return
  }
  watching = true
  const coarse = window.matchMedia('(pointer: coarse)')
  const fine = window.matchMedia('(any-pointer: fine)')
  const detect = (): void => {
    touchOnly.set(coarse.matches && !fine.matches)
  }
  detect()
  coarse.addEventListener('change', detect)
  fine.addEventListener('change', detect)
  window.addEventListener('pointermove', (event) => {
    if (event.pointerType === 'mouse') {
      touchOnly.set(false)
    }
  }, { passive: true })
}

export function touchInput(): ReadableStore<boolean> {
  watchInput()
  return touchOnly
}

const LOCKS = new WeakMap<ShellState, ReadableStore<boolean>>()

// on touch input the model takes no typing until the edit control is pressed, on every shell
export function modelLockOf(shell: ShellState): ReadableStore<boolean> {
  const known = LOCKS.get(shell)
  if (known) {
    return known
  }
  watchInput()
  const locked = (): boolean => {
    return touchOnly.get() && !shell.editing.get()
  }
  // no on-screen keyboard is up while the model is locked, so a key then comes from a hardware keyboard
  const keyPressed = (event: KeyboardEvent): void => {
    if (locked() && event.key !== 'Unidentified' && event.key !== 'Process') {
      touchOnly.set(false)
    }
  }
  const lock: ReadableStore<boolean> = {
    get: locked,
    subscribe: (listener) => {
      const unsubscribeTouch = touchOnly.subscribe(listener)
      const unsubscribeEditing = shell.editing.subscribe(listener)
      window.addEventListener('keydown', keyPressed, true)
      return () => {
        unsubscribeTouch()
        unsubscribeEditing()
        window.removeEventListener('keydown', keyPressed, true)
      }
    },
  }
  LOCKS.set(shell, lock)
  return lock
}

export function useTouchInput(): boolean {
  return useStore(touchInput())
}

export function EditControl({ className }: { className?: string }): React.JSX.Element | null {
  const shell = useShell()
  const touch = useTouchInput()
  const editing = useStore(shell.editing)
  if (!touch) {
    return null
  }
  return (
    <button
      type="button"
      className={`button button-outline edit-control${className ? ` ${className}` : ''}`}
      aria-pressed={editing}
      onClick={() => {
        shell.editing.set(!editing)
      }}
    >
      <Icon name={editing ? 'check' : 'edit'} size="small" />
      {editing ? 'Done' : 'Edit'}
    </button>
  )
}
