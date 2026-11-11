/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'

import './splitter.css'

export const KEYBOARD_STEP = 16

// the splitter holds the pointer, so a drag over an editor or out of a frame keeps going and selects nothing
export function useDrag(onMove: (event: PointerEvent) => void, onEnd: () => void): (event: React.PointerEvent<HTMLElement>) => void {
  return (event) => {
    event.preventDefault()
    const target = event.currentTarget
    target.setPointerCapture(event.pointerId)
    const move = (moveEvent: PointerEvent): void => {
      onMove(moveEvent)
    }
    const stop = (): void => {
      target.removeEventListener('pointermove', move)
      target.removeEventListener('pointerup', stop)
      target.removeEventListener('pointercancel', stop)
      onEnd()
    }
    target.addEventListener('pointermove', move)
    target.addEventListener('pointerup', stop)
    target.addEventListener('pointercancel', stop)
  }
}

export function clamp(value: number, minimum: number, maximum: number): number {
  return Math.min(maximum, Math.max(minimum, value))
}

interface ColumnSplitterProps {
  label: string
  startDrag: (event: React.PointerEvent<HTMLElement>) => void
  reset: () => void
  // by how much the column right of the splitter grows
  resize: (by: number) => void
}

export function ColumnSplitter({ label, startDrag, reset, resize }: ColumnSplitterProps): React.JSX.Element {
  return (
    <div
      className="splitter splitter-vertical"
      role="separator"
      aria-orientation="vertical"
      aria-label={label}
      tabIndex={0}
      title="Drag to resize; double-click to reset"
      onPointerDown={startDrag}
      onDoubleClick={reset}
      onKeyDown={(event) => {
        if (event.key === 'ArrowLeft') {
          resize(KEYBOARD_STEP)
        }
        if (event.key === 'ArrowRight') {
          resize(-KEYBOARD_STEP)
        }
      }}
    >
      <span />
    </div>
  )
}
