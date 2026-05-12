/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React, { useCallback, useEffect, useRef } from 'react'
import Box from '@mui/material/Box'

type Orientation = 'vertical' | 'horizontal'

interface Props {
  /** Container the splitter measures against, used to clamp the resizable side. */
  containerRef: React.RefObject<HTMLDivElement | null>
  /**
   * Vertical splitter (default): width of the trailing pane (right side).
   * Horizontal splitter: height of the trailing pane (bottom side).
   */
  size: number
  onChange: (size: number) => void
  orientation?: Orientation
  /** Minimum size of the leading pane (left/top). */
  minBefore?: number
  /** Minimum size of the trailing pane (right/bottom). */
  minAfter?: number
}

/**
 * 4 px draggable divider. Vertical orientation splits left/right (col-resize); horizontal
 * splits top/bottom (row-resize).
 *
 * <p>The pointermove / pointerup listeners are installed once via a stable bridge that reads
 * the latest props from a ref. This avoids a subtle bug: rebuilding the listeners on every
 * prop change would let an effect cleanup remove the in-flight listeners during a drag,
 * leaving the cursor stuck and the size frozen until the user lifts and re-grabs.
 */
export default function PaneSplitter({
  containerRef,
  size,
  onChange,
  orientation = 'vertical',
  minBefore = 320,
  minAfter = 240,
}: Props): React.JSX.Element {
  const draggingRef = useRef(false)
  const cursor = orientation === 'vertical' ? 'col-resize' : 'row-resize'

  // Mirror the latest props into a ref so the stable listeners always read fresh values.
  const propsRef = useRef({ containerRef, onChange, orientation, minBefore, minAfter })
  useEffect(() => {
    propsRef.current = { containerRef, onChange, orientation, minBefore, minAfter }
  })

  // Install pointermove + pointerup ONCE for the lifetime of the splitter. They're cheap
  // when not dragging (`draggingRef.current === false` returns immediately).
  useEffect(() => {
    const onMove = (event: PointerEvent): void => {
      if (!draggingRef.current) {
        return
      }
      const { containerRef: cref, onChange: notify, orientation: o, minBefore: minB, minAfter: minA } = propsRef.current
      const container = cref.current
      if (!container) {
        return
      }
      const rect = container.getBoundingClientRect()
      const proposed = o === 'vertical' ? rect.right - event.clientX : rect.bottom - event.clientY
      const span = o === 'vertical' ? rect.width : rect.height
      const maxAfter = span - minB
      notify(Math.max(minA, Math.min(maxAfter, proposed)))
    }
    const onUp = (): void => {
      if (!draggingRef.current) {
        return
      }
      draggingRef.current = false
      document.body.style.cursor = ''
      document.body.style.userSelect = ''
    }
    window.addEventListener('pointermove', onMove)
    window.addEventListener('pointerup', onUp)
    return () => {
      window.removeEventListener('pointermove', onMove)
      window.removeEventListener('pointerup', onUp)
      // Restore body cursor in case unmount fires mid-drag.
      if (draggingRef.current) {
        draggingRef.current = false
        document.body.style.cursor = ''
        document.body.style.userSelect = ''
      }
    }
  }, [])

  const handlePointerDown = useCallback(
    (event: React.PointerEvent<HTMLDivElement>) => {
      event.preventDefault()
      draggingRef.current = true
      document.body.style.cursor = cursor
      document.body.style.userSelect = 'none'
    },
    [cursor],
  )

  const isVertical = orientation === 'vertical'
  // Visible 1 px track inside a 5 px hit area for easy grabbing.
  return (
    <Box
      role="separator"
      aria-orientation={isVertical ? 'vertical' : 'horizontal'}
      aria-valuenow={size}
      onPointerDown={handlePointerDown}
      sx={{
        position: 'relative',
        flex: '0 0 auto',
        cursor,
        bgcolor: 'transparent',
        ...(isVertical
          ? { width: '5px' }
          : { height: '5px' }),
        '&::after': {
          content: '""',
          position: 'absolute',
          bgcolor: 'var(--surface-border)',
          ...(isVertical
            ? { top: 0, bottom: 0, left: '2px', width: '1px' }
            : { left: 0, right: 0, top: '2px', height: '1px' }),
        },
        '&:hover::after': {
          bgcolor: 'var(--accent)',
        },
      }}
    />
  )
}
