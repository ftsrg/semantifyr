/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it, vi } from 'vitest';
import { useEffect, useRef } from 'react';
import { render } from '@testing-library/react';
import PaneSplitter from '../components/shell/PaneSplitter';

interface HostProps {
  rect: DOMRect;
  size: number;
  onChange: (next: number) => void;
  orientation?: 'vertical' | 'horizontal';
  minBefore?: number;
  minAfter?: number;
}

function Host({ rect, size, onChange, orientation, minBefore, minAfter }: HostProps): React.JSX.Element {
  const ref = useRef<HTMLDivElement | null>(null);
  useEffect(() => {
    if (ref.current) {
      ref.current.getBoundingClientRect = () => rect;
    }
  }, [rect]);
  return (
    <div ref={ref} data-testid="host">
      <PaneSplitter
        containerRef={ref}
        size={size}
        onChange={onChange}
        orientation={orientation ?? 'vertical'}
        minBefore={minBefore ?? 0}
        minAfter={minAfter ?? 0}
      />
    </div>
  );
}

function dispatchPointer(type: 'pointermove' | 'pointerup', clientX: number, clientY: number): void {
  // jsdom's PointerEvent constructor exists but is sparse; fall back to a MouseEvent shape
  // dressed up as a PointerEvent. The component reads only clientX / clientY.
  const event = new MouseEvent(type, { bubbles: true, clientX, clientY }) as unknown as PointerEvent;
  window.dispatchEvent(event);
}

function makeRect(left: number, top: number, width: number, height: number): DOMRect {
  return {
    left,
    top,
    right: left + width,
    bottom: top + height,
    width,
    height,
    x: left,
    y: top,
    toJSON: () => ({}),
  } as DOMRect;
}

describe('PaneSplitter', () => {
  it('vertical orientation: drag computes trailing-pane width as right - clientX', () => {
    const onChange = vi.fn();
    const rect = makeRect(0, 0, 1000, 600);
    const { getByRole } = render(
      <Host rect={rect} size={300} onChange={onChange} orientation="vertical" />,
    );

    const handle = getByRole('separator');
    handle.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, clientX: 700, clientY: 100 }));
    dispatchPointer('pointermove', 600, 100);
    dispatchPointer('pointerup', 600, 100);

    // Trailing pane width = rect.right(1000) - clientX(600) = 400.
    expect(onChange).toHaveBeenLastCalledWith(400);
  });

  it('vertical orientation: clamps trailing pane to minAfter on the high end', () => {
    const onChange = vi.fn();
    const rect = makeRect(0, 0, 1000, 600);
    const { getByRole } = render(
      <Host rect={rect} size={300} onChange={onChange} orientation="vertical" minAfter={200} />,
    );
    const handle = getByRole('separator');
    handle.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, clientX: 0, clientY: 0 }));
    // Drag to clientX=950 -> proposed = 50, clamps up to 200.
    dispatchPointer('pointermove', 950, 0);
    dispatchPointer('pointerup', 950, 0);
    expect(onChange).toHaveBeenLastCalledWith(200);
  });

  it('vertical orientation: clamps trailing pane to leading-pane minimum', () => {
    const onChange = vi.fn();
    const rect = makeRect(0, 0, 1000, 600);
    const { getByRole } = render(
      <Host rect={rect} size={300} onChange={onChange} orientation="vertical" minBefore={400} />,
    );
    const handle = getByRole('separator');
    handle.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, clientX: 0, clientY: 0 }));
    // Drag to clientX=100 -> proposed = 900, clamps down to maxAfter = span(1000) - minBefore(400) = 600.
    dispatchPointer('pointermove', 100, 0);
    dispatchPointer('pointerup', 100, 0);
    expect(onChange).toHaveBeenLastCalledWith(600);
  });

  it('horizontal orientation: drag computes trailing-pane height as bottom - clientY', () => {
    const onChange = vi.fn();
    const rect = makeRect(0, 0, 1000, 600);
    const { getByRole } = render(
      <Host rect={rect} size={200} onChange={onChange} orientation="horizontal" />,
    );
    const handle = getByRole('separator');
    handle.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, clientX: 0, clientY: 0 }));
    dispatchPointer('pointermove', 0, 400);
    dispatchPointer('pointerup', 0, 400);

    // Trailing pane height = bottom(600) - clientY(400) = 200.
    expect(onChange).toHaveBeenLastCalledWith(200);
  });

  it('does not fire onChange after pointerup, so a stray late move is ignored', () => {
    const onChange = vi.fn();
    const rect = makeRect(0, 0, 1000, 600);
    const { getByRole } = render(
      <Host rect={rect} size={300} onChange={onChange} orientation="vertical" />,
    );
    const handle = getByRole('separator');
    handle.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, clientX: 700, clientY: 100 }));
    dispatchPointer('pointermove', 600, 100);
    dispatchPointer('pointerup', 600, 100);
    onChange.mockClear();
    // A move that arrives after the user released the pointer is ignored.
    dispatchPointer('pointermove', 500, 100);
    expect(onChange).not.toHaveBeenCalled();
  });
});
