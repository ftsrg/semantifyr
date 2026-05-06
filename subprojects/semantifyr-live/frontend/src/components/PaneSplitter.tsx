/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React, { useCallback, useEffect, useRef } from 'react';
import Box from '@mui/material/Box';

type Orientation = 'vertical' | 'horizontal';

interface Props {
  /** Container the splitter measures against, used to clamp the resizable side. */
  containerRef: React.RefObject<HTMLDivElement | null>;
  /**
   * Vertical splitter (default): width of the trailing pane (right side).
   * Horizontal splitter: height of the trailing pane (bottom side).
   */
  size: number;
  onChange: (size: number) => void;
  orientation?: Orientation;
  /** Minimum size of the leading pane (left/top). */
  minBefore?: number;
  /** Minimum size of the trailing pane (right/bottom). */
  minAfter?: number;
}

/**
 * 4 px draggable divider. Vertical orientation splits left/right (col-resize); horizontal splits
 * top/bottom (row-resize).
 *
 * <p>While dragging we listen on `window` so the cursor can leave the splitter without losing
 * the grab. We clamp via the container's bounding rect so the user can't squash either side
 * below {@link Props.minBefore} / {@link Props.minAfter}.
 */
export default function PaneSplitter({
  containerRef,
  size,
  onChange,
  orientation = 'vertical',
  minBefore = 320,
  minAfter = 240,
}: Props): React.JSX.Element {
  const draggingRef = useRef(false);
  const cursor = orientation === 'vertical' ? 'col-resize' : 'row-resize';

  const handlePointerMove = useCallback(
    (event: PointerEvent) => {
      if (!draggingRef.current) return;
      const container = containerRef.current;
      if (!container) return;
      const rect = container.getBoundingClientRect();
      const proposed = orientation === 'vertical'
        ? rect.right - event.clientX
        : rect.bottom - event.clientY;
      const span = orientation === 'vertical' ? rect.width : rect.height;
      const maxAfter = span - minBefore;
      const next = Math.max(minAfter, Math.min(maxAfter, proposed));
      onChange(next);
    },
    [containerRef, onChange, orientation, minBefore, minAfter],
  );

  const handlePointerUp = useCallback(() => {
    draggingRef.current = false;
    document.body.style.cursor = '';
    document.body.style.userSelect = '';
    window.removeEventListener('pointermove', handlePointerMove);
    window.removeEventListener('pointerup', handlePointerUp);
  }, [handlePointerMove]);

  const handlePointerDown = useCallback(
    (event: React.PointerEvent<HTMLDivElement>) => {
      event.preventDefault();
      draggingRef.current = true;
      document.body.style.cursor = cursor;
      document.body.style.userSelect = 'none';
      window.addEventListener('pointermove', handlePointerMove);
      window.addEventListener('pointerup', handlePointerUp);
    },
    [handlePointerMove, handlePointerUp, cursor],
  );

  // Defensive cleanup on unmount.
  useEffect(() => {
    return () => {
      window.removeEventListener('pointermove', handlePointerMove);
      window.removeEventListener('pointerup', handlePointerUp);
    };
  }, [handlePointerMove, handlePointerUp]);

  const isVertical = orientation === 'vertical';
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
  );
}
