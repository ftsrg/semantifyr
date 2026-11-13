/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

// @vitest-environment jsdom

import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { decodeCompressedBase64Url } from '@semantifyr/editor-common';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { EmbeddedExample } from '../components/LiveEmbed/EmbeddedExample';

// Goal: an embedded example holds no frame until pressed, opens in place once live shows the model, and closes back to its control

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
});

function example(view: 'embed' | 'edit-only' = 'edit-only'): void {
  render(<EmbeddedExample model={MODEL} shown="var value: int := 0" lines={{ first: 2, last: 2 }} language="oxsts" view={view} />);
}

async function frame(): Promise<HTMLIFrameElement> {
  return await screen.findByTitle<HTMLIFrameElement>('Semantifyr live editor');
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
    expect(screen.getByRole('button').textContent).toBe('Edit');
    expect(screen.queryByTitle('Semantifyr live editor')).toBeNull();
  });

  it('offers Run where it verifies', () => {
    example('embed');

    expect(screen.getByRole('button').textContent).toBe('Run');
  });

  it('loads the whole model at the lines shown under its block, which stays until live shows the model', async () => {
    example();

    fireEvent.click(screen.getByRole('button', { name: 'Edit' }));
    const loading = await frame();

    expect(screen.getByRole('status').textContent).toBe('Opening the editor');
    expect(screen.getByText('var value: int := 0').closest('[aria-busy]')?.getAttribute('aria-busy')).toBe('true');
    const address = new URL(loading.src);
    expect(address.origin + address.pathname).toBe('https://live.example/oxsts');
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

    fireEvent.click(screen.getByRole('button', { name: 'Edit' }));
    await frame();
    act(() => {
      window.dispatchEvent(new MessageEvent('message', { data: { type: 'semantifyr-live:shown' }, origin: 'https://live.example', source: window }));
    });

    expect(screen.getByRole('status').textContent).toBe('Opening the editor');
  });

  it('closes back to its block with the focus on its control', async () => {
    const scroll = vi.spyOn(window, 'scrollBy').mockImplementation(() => {});
    example();

    fireEvent.click(screen.getByRole('button', { name: 'Edit' }));
    report(await frame(), 'semantifyr-live:shown');
    fireEvent.click(screen.getByRole('button', { name: 'Close the editor' }));

    expect(screen.queryByTitle('Semantifyr live editor')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Close the editor' })).toBeNull();
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Edit' }));
    expect(screen.getByText('var value: int := 0').closest('[hidden]')).toBeNull();
    expect(scroll).toHaveBeenCalledOnce();
  });
});
