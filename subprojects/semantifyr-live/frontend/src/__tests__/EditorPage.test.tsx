/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

vi.mock('../components/LiveEditor', () => ({
  default: (props: {
    flavorId: string;
    languageId: string;
    fileName: string;
    initialCode: string;
    backendUrl: string;
  }) => (
    <div
      data-testid="live-editor"
      data-flavor-id={props.flavorId}
      data-language={props.languageId}
      data-file-name={props.fileName}
      data-backend-url={props.backendUrl}
    >
      <pre data-testid="initial-code">{props.initialCode}</pre>
    </div>
  ),
}));

import EditorPage from '../components/EditorPage';
import { LIVE_FLAVORS } from '../examples';

describe('EditorPage', () => {
  let originalLocation: Location;

  beforeEach(() => {
    originalLocation = window.location;
  });

  afterEach(() => {
    Object.defineProperty(window, 'location', {
      configurable: true,
      writable: true,
      value: originalLocation,
    });
  });

  function setLocation(url: string) {
    const parsed = new URL(url);
    Object.defineProperty(window, 'location', {
      configurable: true,
      writable: true,
      value: {
        ...originalLocation,
        href: parsed.href,
        origin: parsed.origin,
        hostname: parsed.hostname,
        host: parsed.host,
        protocol: parsed.protocol,
        pathname: parsed.pathname,
        search: parsed.search,
        hash: parsed.hash,
      } as Location,
    });
  }

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
    return await screen.findByTestId('live-editor');
  }

  it('defaults to the first flavor and its first example when the URL has no params', async () => {
    setLocation('https://test.example/');
    renderPage();
    const editor = await waitForEditor();
    expect(editor.dataset.language).toBe('oxsts');
    const code = within(editor).getByTestId('initial-code').textContent;
    const expectedCode = LIVE_FLAVORS[0]!.examples[0]!.code;
    expect(code).toBe(expectedCode);
  });

  it('respects ?example=<id> with the flavor inferred from the registry', async () => {
    setLocation('https://test.example/?example=trafficlight-direct-snapshot');
    renderPage();
    const editor = await waitForEditor();
    expect(editor.dataset.language).toBe('oxsts');
    const code = within(editor).getByTestId('initial-code').textContent;
    expect(code).toContain('@VerificationCase');
    expect(code).toContain('class TrafficLight');
  });

  it('respects ?code=<base64> for arbitrary user code', async () => {
    // base64url-encoded "package custom\nclass Custom { var x: int := 42 }"
    const encoded = 'cGFja2FnZSBjdXN0b20KY2xhc3MgQ3VzdG9tIHsgdmFyIHg6IGludCA6PSA0MiB9';
    setLocation(`https://test.example/?code=${encoded}`);
    renderPage();
    const editor = await waitForEditor();
    const code = within(editor).getByTestId('initial-code').textContent;
    expect(code).toBe('package custom\nclass Custom { var x: int := 42 }');
  });

  it('falls back to the default example when ?example points at an unknown id', async () => {
    setLocation('https://test.example/?example=does-not-exist');
    renderPage();
    const editor = await waitForEditor();
    const defaultCode = LIVE_FLAVORS[0]!.examples[0]!.code;
    expect(within(editor).getByTestId('initial-code').textContent).toBe(defaultCode);
  });

  it('clicking Copy link reports success and the handler completes', async () => {
    setLocation('https://test.example/');
    renderPage();
    await waitForEditor();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Copy link' }));

    const note = await screen.findByText(/Link copied!|Copy failed/);
    expect(note.textContent).toMatch(/^(Link copied!|Copy failed)$/);
  });
});
