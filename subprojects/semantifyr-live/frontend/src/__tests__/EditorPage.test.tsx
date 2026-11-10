/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

vi.mock('../components/editor/LiveEditor', () => ({
  default: (properties: {
    flavorId: string;
    languageId: string;
    fileName: string;
    initialCode: string;
    backendUrl: string;
  }) => (
    <div
      data-testid="live-editor"
      data-flavor-id={properties.flavorId}
      data-language={properties.languageId}
      data-file-name={properties.fileName}
      data-backend-url={properties.backendUrl}
    >
      <pre data-testid="initial-code">{properties.initialCode}</pre>
    </div>
  ),
}));

import EditorPage from '../components/EditorPage';
import { liveFlavors } from '../examples';
import { encodeCompressedBase64Url } from '@semantifyr/editor-common';
import { setLocation } from './helpers/location';
import { servedFlavors } from './fixtures/flavors';

const defaultFlavor = liveFlavors(servedFlavors)[0];

function serveFlavors(): void {
  vi.stubGlobal('fetch', vi.fn(async (url: string) => {
    if (url.endsWith('/api/flavors')) {
      return new Response(JSON.stringify({ flavors: servedFlavors }));
    }
    return new Response(null, { status: 404 });
  }));
}

describe('EditorPage', () => {
  let originalLocation: Location;

  beforeEach(() => {
    originalLocation = window.location;
    serveFlavors();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    Object.defineProperty(window, 'location', {
      configurable: true,
      writable: true,
      value: originalLocation,
    });
  });

  function renderPage(): ReturnType<typeof render> {
    return render(
      <EditorPage
        backendUrl="https://test.example"
        colorMode="dark"
        colorModePreference="dark"
        onToggleColorMode={() => {}}
      />,
    );
  }

  async function waitForEditor(): Promise<HTMLElement> {
    return await screen.findByTestId('live-editor', {}, { timeout: 4000 });
  }

  it('defaults to the first flavor and its first example when the URL has no params', async () => {
    setLocation('https://test.example/');
    renderPage();
    const editor = await waitForEditor();
    expect(editor.dataset.language).toBe('oxsts');
    const code = within(editor).getByTestId('initial-code').textContent;
    const expectedCode = defaultFlavor.examples[0]!.code;
    expect(code).toBe(expectedCode);
  });

  it('opens the flavor named by ?mode= with its first example', async () => {
    setLocation('https://test.example/?mode=gamma');
    renderPage();
    const editor = await waitForEditor();
    expect(editor.dataset.flavorId).toBe('gamma');
    expect(editor.dataset.language).toBe('gamma');
  });

  it('opens the first served flavor for an unknown ?mode=', async () => {
    setLocation('https://test.example/?mode=cobol');
    renderPage();
    const editor = await waitForEditor();
    expect(editor.dataset.flavorId).toBe('oxsts');
    expect(within(editor).getByTestId('initial-code').textContent).toBe(defaultFlavor.examples[0]!.code);
  });

  it('waits for the server before showing the editor when the flavors do not load', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => {
      return new Response(null, { status: 502 });
    }));
    setLocation('https://test.example/');
    renderPage();
    expect(await screen.findByText('Waiting for the Semantifyr server...')).toBeInTheDocument();
    expect(screen.queryByTestId('live-editor')).toBeNull();
  });

  it('respects ?example=<id> with the flavor inferred from the registry', async () => {
    setLocation('https://test.example/?example=basics');
    renderPage();
    const editor = await waitForEditor();
    expect(editor.dataset.language).toBe('oxsts');
    const code = within(editor).getByTestId('initial-code').textContent;
    expect(code).toContain('@VerificationCase');
    expect(code).toContain('class TrafficLight');
  });

  it('respects ?code=<gzip+base64url> for arbitrary user code', async () => {
    const source = 'package custom\nclass Custom { var x: int := 42 }';
    const encoded = await encodeCompressedBase64Url(source);
    setLocation(`https://test.example/?code=${encodeURIComponent(encoded)}`);
    renderPage();
    const editor = await waitForEditor();
    // resolveInitialState is async (gzip decode); wait for the state to land before asserting.
    await waitFor(() => {
      expect(within(editor).getByTestId('initial-code').textContent).toBe(source);
    });
  });

  it('titles the page with the example it opens', async () => {
    setLocation('https://test.example/?example=basics');
    renderPage();
    await waitForEditor();
    const basics = defaultFlavor.examples.find((example) => example.id === 'basics')!;
    await waitFor(() => {
      expect(document.title).toBe(`${basics.label} - Semantifyr Live`);
    });
  });

  it('does not title code from a link with the default example', async () => {
    const encoded = await encodeCompressedBase64Url('package custom\nclass Custom { var x: int := 42 }');
    setLocation(`https://test.example/?mode=oxsts&code=${encodeURIComponent(encoded)}`);
    renderPage();
    await waitForEditor();
    await waitFor(() => {
      expect(document.title).toBe('Shared model - Semantifyr Live');
    });
  });

  it('copies a link to code from a link without naming an example', async () => {
    const encoded = await encodeCompressedBase64Url('package custom\nclass Custom { var x: int := 42 }');
    setLocation(`https://test.example/?mode=oxsts&code=${encodeURIComponent(encoded)}`);
    renderPage();
    await waitForEditor();
    await waitFor(() => {
      expect(document.title).toBe('Shared model - Semantifyr Live');
    });
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Copy link' }));

    await screen.findByText('Link copied!');
    const copied = new URL(await navigator.clipboard.readText());
    expect(copied.searchParams.has('example')).toBe(false);
    expect(copied.searchParams.get('code')).not.toBeNull();
  });

  it('copies a link to an unchanged example without its code', async () => {
    setLocation('https://test.example/?example=basics');
    renderPage();
    await waitForEditor();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Copy link' }));

    await screen.findByText('Link copied!');
    const copied = new URL(await navigator.clipboard.readText());
    expect(copied.searchParams.get('example')).toBe('basics');
    expect(copied.searchParams.has('code')).toBe(false);
  });

  it('falls back to the default example when ?example points at an unknown id', async () => {
    setLocation('https://test.example/?example=does-not-exist');
    renderPage();
    const editor = await waitForEditor();
    const defaultCode = defaultFlavor.examples[0]!.code;
    expect(within(editor).getByTestId('initial-code').textContent).toBe(defaultCode);
  });

  it('clicking Copy link reports back to the user (success or failure depending on jsdom clipboard)', async () => {
    setLocation('https://test.example/');
    renderPage();
    await waitForEditor();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Copy link' }));

    const note = await screen.findByText(/Link copied!|Copy failed/);
    expect(note.textContent).toMatch(/^(Link copied!|Copy failed)$/);
  });
});
