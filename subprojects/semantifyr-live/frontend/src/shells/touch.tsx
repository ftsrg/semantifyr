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
const MOZ_SOURCE_MOUSE = 1
let watching = false

function watchInput(): void {
  if (watching || typeof window === 'undefined') {
    return
  }
  watching = true
  // not any-pointer: a phone's pen digitizer reports a fine pointer among any
  const coarse = window.matchMedia('(pointer: coarse)')
  const detect = (): void => {
    touchOnly.set(coarse.matches)
  }
  detect()
  coarse.addEventListener('change', detect)
  window.addEventListener('pointermove', (event) => {
    if (event.pointerType === 'mouse') {
      touchOnly.set(false)
    }
  }, { passive: true })
  // Firefox may send a mouse no pointermove; its mozInputSource tells a mouse from a tap's compatibility events
  window.addEventListener('mousemove', (event) => {
    if ((event as MouseEvent & { mozInputSource?: number }).mozInputSource === MOZ_SOURCE_MOUSE) {
      touchOnly.set(false)
    }
  }, { passive: true })
}

export function touchInput(): ReadableStore<boolean> {
  watchInput()
  return touchOnly
}

// an on-screen keyboard is up only over an element that takes text
function takesText(target: EventTarget | null): boolean {
  if (target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement) {
    return !target.readOnly
  }
  return target instanceof HTMLElement && (target.isContentEditable || (target as { editContext?: unknown }).editContext != null)
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
  // the locked model takes no text, so a named key outside every field that does comes from a hardware keyboard
  const keyPressed = (event: KeyboardEvent): void => {
    if (locked() && event.key !== 'Unidentified' && event.key !== 'Process' && !takesText(event.target)) {
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
