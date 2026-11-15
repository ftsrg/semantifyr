/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

// @vitest-environment jsdom

import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { hydrateRoot } from 'react-dom/client';
import { renderToString } from 'react-dom/server';
import { afterEach, describe, expect, it, vi } from 'vitest';

import PhaseWalk, { STEP_MILLISECONDS } from '../components/Phases/PhaseWalk';
import type { WalkStep } from '../components/Phases/walks';

// Goal: the walk steps only when asked, keeps its step and the focus, keeps its step across the switch of views, opens in the view the screen's width calls for from its first paint, marks a change by more than its colour without adding to a copy, and moves nothing for a visitor who asks for less motion

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

  it('serves markup that holds both views for the style sheet to choose, and hydrates on a narrow screen without a mismatch', () => {
    const element = <PhaseWalk steps={STEPS} label="The walk" />;
    const container = document.createElement('div');
    container.innerHTML = renderToString(element);
    expect(container.querySelectorAll('figure')).toHaveLength(3);
    expect(container.querySelector('[aria-pressed]')).toBeNull();
    document.body.appendChild(container);

    screenWith({ narrow: true });
    const errors: unknown[] = [];
    act(() => {
      hydrateRoot(container, element, {
        onRecoverableError: (error) => {
          errors.push(error);
        },
      });
    });

    expect(errors).toEqual([]);
    expect(container.querySelectorAll('figure')).toHaveLength(1);
    container.remove();
  });

  it('keeps its step when the view is switched', () => {
    screenWith({});
    walk();
    press('Next');

    press('One pane');

    expect(announced()).toBe('Step 2 of 3. Then c comes.');
    expect(screen.getByText('two.oxsts')).toBeTruthy();
  });

  it('marks a removed and an added line by a strike and a spoken word, and a copy of the line is its code alone', () => {
    screenWith({ narrow: true });
    walk();
    const removed = document.querySelector('del');
    expect(removed?.textContent).toBe('a');
    expect(removed?.parentElement?.textContent).toBe('a\n');
    expect(removed?.parentElement?.querySelector('[data-spoken]')?.getAttribute('data-spoken')).toBe('Removed: ');

    press('Next');
    const added = document.querySelector('ins');
    expect(added?.textContent).toBe('c');
    expect(added?.parentElement?.textContent).toBe('c\n');
    expect(added?.parentElement?.querySelector('[data-spoken]')?.getAttribute('data-spoken')).toBe('Added: ');
  });

  it('keeps Back and Next focusable at the ends, where they do nothing', () => {
    screenWith({});
    walk();
    const back = screen.getByRole('button', { name: 'Back' });
    back.focus();

    press('Back');

    expect(back.hasAttribute('disabled')).toBe(false);
    expect(back.getAttribute('aria-disabled')).toBe('true');
    expect(document.activeElement).toBe(back);
    expect(announced()).toBe('Step 1 of 3. First a goes.');
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
    expect(screen.getByRole('button', { name: 'Play' }).hasAttribute('aria-pressed')).toBe(false);
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
