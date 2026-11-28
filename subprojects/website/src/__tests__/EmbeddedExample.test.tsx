/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

// @vitest-environment jsdom

import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { decodeCompressedBase64Url } from '@semantifyr/editor-common';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { EmbeddedExample, UNANSWERED_AFTER_MILLISECONDS } from '../components/LiveEmbed/EmbeddedExample';

vi.mock('@docusaurus/theme-common', () => {
  return {
    useColorMode: () => {
      return { colorMode: 'light' };
    },
  };
});

vi.mock('@site/src/lib/live', () => {
  return {
    useLiveBackendUrl: () => {
      return 'https://live.example';
    },
  };
});

const MODEL = 'class Counter {\nvar value: int := 0\n}';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.useRealTimers();
});

function example(view: 'embed' | 'edit-only' = 'edit-only'): void {
  render(<EmbeddedExample model={MODEL} shown="var value: int := 0" lines={{ first: 2, last: 2 }} language="oxsts" view={view} />);
}

async function frame(): Promise<HTMLIFrameElement> {
  const found = await screen.findByTitle<HTMLIFrameElement>('Semantifyr Live');
  // flushes the effect that registers the frame's message listener
  await act(async () => {});
  return found;
}

function report(from: HTMLIFrameElement, type: string): void {
  act(() => {
    window.dispatchEvent(new MessageEvent('message', { data: { type }, origin: 'https://live.example', source: from.contentWindow }));
  });
}

describe('an embedded example', () => {
  it('is a code block with one control and no frame until pressed', () => {
    example();

    expect(screen.getByText('var value: int := 0')).toBeTruthy();
    expect(screen.getByRole('button').textContent).toBe('Open');
    expect(screen.queryByTitle('Semantifyr Live')).toBeNull();
  });

  it('offers Open where it verifies too', () => {
    example('embed');

    expect(screen.getByRole('button').textContent).toBe('Open');
  });

  it('loads the whole model at the lines shown under its block, which stays until live shows the model', async () => {
    example();

    fireEvent.click(screen.getByRole('button', { name: 'Open' }));
    const loading = await frame();

    expect(screen.getByRole('status').textContent).toBe('Opening the editor');
    expect(screen.getByText('var value: int := 0').closest('[aria-busy]')?.getAttribute('aria-busy')).toBe('true');
    const address = new URL(loading.src);
    expect(address.origin + address.pathname).toBe('https://live.example/language');
    expect(Object.fromEntries(address.searchParams)).toEqual({ view: 'edit-only', theme: 'light', lines: '2-2' });
    expect(await decodeCompressedBase64Url(new URLSearchParams(address.hash.slice(1)).get('model') ?? '')).toBe(MODEL);
    expect(screen.getByRole('button', { name: 'Close the editor' })).toBeTruthy();

    report(loading, 'semantifyr-live:shown');

    expect(screen.queryByRole('status')).toBeNull();
    expect(screen.getByText('var value: int := 0').closest('[hidden]')).not.toBeNull();
    expect(await frame()).toBe(loading);
  });

  it('takes no sign from another frame', async () => {
    example();

    fireEvent.click(screen.getByRole('button', { name: 'Open' }));
    await frame();
    act(() => {
      window.dispatchEvent(new MessageEvent('message', { data: { type: 'semantifyr-live:shown' }, origin: 'https://live.example', source: window }));
    });

    expect(screen.getByRole('status').textContent).toBe('Opening the editor');
  });

  it('closes back to its block with the focus on its control', async () => {
    const scroll = vi.spyOn(window, 'scrollBy').mockImplementation(() => {});
    example();

    fireEvent.click(screen.getByRole('button', { name: 'Open' }));
    report(await frame(), 'semantifyr-live:shown');
    fireEvent.click(screen.getByRole('button', { name: 'Close the editor' }));

    expect(screen.queryByTitle('Semantifyr Live')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Close the editor' })).toBeNull();
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Open' }));
    expect(screen.getByText('var value: int := 0').closest('[hidden]')).toBeNull();
    expect(scroll).toHaveBeenCalledOnce();
  });
});

describe('an embedded example live does not answer', () => {
  async function waiting(): Promise<HTMLIFrameElement> {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'], shouldAdvanceTime: true });
    example();
    fireEvent.click(screen.getByRole('button', { name: 'Open' }));
    return await frame();
  }

  function waitOut(): void {
    act(() => {
      vi.advanceTimersByTime(UNANSWERED_AFTER_MILLISECONDS);
    });
  }

  it('says so after the bound, offers live in a new tab and keeps loading', async () => {
    const loading = await waiting();

    waitOut();

    expect(screen.getByRole('status').textContent).toBe('Semantifyr Live is not answering');
    const link = await screen.findByRole('link', { name: 'Open in Live' });
    expect(link.getAttribute('target')).toBe('_blank');
    expect(new URL(link.getAttribute('href') ?? '').pathname).toBe('/language');
    expect(await frame()).toBe(loading);
  });

  it('opens the editor and drops the notice when live shows the model after the bound', async () => {
    const loading = await waiting();
    waitOut();

    report(loading, 'semantifyr-live:shown');

    expect(screen.queryByRole('status')).toBeNull();
    expect(screen.queryByRole('link', { name: 'Open in Live' })).toBeNull();
    expect(screen.getByText('var value: int := 0').closest('[hidden]')).not.toBeNull();
  });

  it('never says so when live shows the model within the bound', async () => {
    report(await waiting(), 'semantifyr-live:shown');

    waitOut();

    expect(screen.queryByText('Semantifyr Live is not answering')).toBeNull();
  });
});
