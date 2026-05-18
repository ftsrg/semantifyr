/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { useCallback, useEffect, useRef } from 'react'
import Box from '@mui/material/Box'

type Orientation = 'vertical' | 'horizontal'

interface Props {
  containerRef: React.RefObject<HTMLDivElement | null>
  size: number
  onChange: (size: number) => void
  orientation?: Orientation
  minBefore?: number
  minAfter?: number
}

// Pointer listeners are installed once and read latest props via a ref; rebuilding them
// every prop change would tear down in-flight handlers mid-drag.
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

  const propsRef = useRef({ containerRef, onChange, orientation, minBefore, minAfter })
  useEffect(() => {
    propsRef.current = { containerRef, onChange, orientation, minBefore, minAfter }
  })

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
  // 1 px visible track in a 5 px hit area.
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
