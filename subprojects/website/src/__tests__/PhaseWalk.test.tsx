/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

// @vitest-environment jsdom

import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import PhaseWalk, { STEP_MILLISECONDS } from '../components/Phases/PhaseWalk';
import type { WalkStep } from '../components/Phases/walks';

// Goal: the walk steps only when asked, keeps its step across the switch of views, opens in the view the screen's width calls for, marks a change by more than its colour and moves nothing for a visitor who asks for less motion

const STEPS: WalkStep[] = [
  {
    caption: 'First `a` goes.',
    before: { file: 'zero.oxsts', text: 'a\nb' },
    after: { file: 'one.oxsts', text: 'b' },
  },
  {
    caption: 'Then `c` comes.',
    before: { file: 'one.oxsts', text: 'b' },
    after: { file: 'two.oxsts', text: 'b\nc' },
  },
  {
    caption: 'Last `d` comes.',
    before: { file: 'two.oxsts', text: 'b\nc' },
    after: { file: 'three.oxsts', text: 'b\nc\nd' },
  },
];

function screenWith({ narrow = false, lessMotion = false }: { narrow?: boolean, lessMotion?: boolean }): void {
  vi.stubGlobal('matchMedia', (query: string) => {
    return {
      matches: (query.includes('max-width') && narrow) || (query.includes('reduced-motion') && lessMotion),
      addEventListener: () => {},
      removeEventListener: () => {},
    };
  });
}

function walk(): void {
  render(<PhaseWalk steps={STEPS} label="The walk" />);
}

function announced(): string {
  return document.querySelector('[aria-live]')?.textContent ?? '';
}

function press(name: string): void {
  fireEvent.click(screen.getByRole('button', { name }));
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe('a walk through a phase', () => {
  it('announces its step and steps forward and back when asked', () => {
    screenWith({});
    walk();

    expect(announced()).toBe('Step 1 of 3. First a goes.');
    press('Next');
    expect(announced()).toBe('Step 2 of 3. Then c comes.');
    press('Back');
    expect(announced()).toBe('Step 1 of 3. First a goes.');
  });

  it('opens side by side on a wide screen and in one pane on a narrow one', () => {
    screenWith({});
    walk();
    expect(screen.getAllByRole('figure')).toHaveLength(2);
    cleanup();

    screenWith({ narrow: true });
    walk();
    expect(screen.getAllByRole('figure')).toHaveLength(1);
    expect(screen.getByRole('button', { name: 'One pane' }).getAttribute('aria-pressed')).toBe('true');
  });

  it('keeps its step when the view is switched', () => {
    screenWith({});
    walk();
    press('Next');

    press('One pane');

    expect(announced()).toBe('Step 2 of 3. Then c comes.');
    expect(screen.getByText('two.oxsts')).toBeTruthy();
  });

  it('marks a removed and an added line by a sign, a strike and a word, not by colour alone', () => {
    screenWith({ narrow: true });
    walk();
    const removed = document.querySelector('del');
    expect(removed?.textContent).toBe('a');
    expect(removed?.closest('span')?.textContent).toBe('-Removed: a\n');

    press('Next');
    const added = document.querySelector('ins');
    expect(added?.textContent).toBe('c');
    expect(added?.closest('span')?.textContent).toBe('+Added: c\n');
  });

  it('plays only when asked, and pauses', () => {
    vi.useFakeTimers();
    screenWith({});
    walk();

    act(() => {
      vi.advanceTimersByTime(STEP_MILLISECONDS * 2);
    });
    expect(announced()).toBe('Step 1 of 3. First a goes.');

    press('Play');
    act(() => {
      vi.advanceTimersByTime(STEP_MILLISECONDS);
    });
    expect(announced()).toBe('Step 2 of 3. Then c comes.');

    press('Pause');
    act(() => {
      vi.advanceTimersByTime(STEP_MILLISECONDS * 2);
    });
    expect(announced()).toBe('Step 2 of 3. Then c comes.');
  });

  it('stops playing at its last step', () => {
    vi.useFakeTimers();
    screenWith({});
    walk();

    press('Play');
    act(() => {
      vi.advanceTimersByTime(STEP_MILLISECONDS);
    });
    act(() => {
      vi.advanceTimersByTime(STEP_MILLISECONDS);
    });

    expect(announced()).toBe('Step 3 of 3. Last d comes.');
    expect(screen.getByRole('button', { name: 'Play' }).getAttribute('aria-pressed')).toBe('false');
  });

  it('lets a new line appear only for a visitor who has not asked for less motion', () => {
    screenWith({ narrow: true });
    walk();
    press('Next');
    expect(document.querySelector('ins')?.closest('span')?.className).toMatch(/appearing/);
    cleanup();

    screenWith({ narrow: true, lessMotion: true });
    walk();
    press('Next');
    expect(document.querySelector('ins')?.closest('span')?.className).not.toMatch(/appearing/);
  });
});
