/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

vi.mock('../components/editor/LiveEditor', () => ({
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
import { encodeCompressedBase64Url } from '../lib/api/urls';
import { setLocation } from './helpers/location';

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

  it('falls back to the default example when ?example points at an unknown id', async () => {
    setLocation('https://test.example/?example=does-not-exist');
    renderPage();
    const editor = await waitForEditor();
    const defaultCode = LIVE_FLAVORS[0]!.examples[0]!.code;
    expect(within(editor).getByTestId('initial-code').textContent).toBe(defaultCode);
  });

  it('clicking Copy link reports back to the user (success or failure depending on jsdom clipboard)', async () => {
    setLocation('https://test.example/');
    renderPage();
    await waitForEditor();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Copy link' }));

    // jsdom does not ship a writable navigator.clipboard, so the handler typically lands in
    // the catch branch and surfaces "Copy failed". When the harness *does* provide a working
    // clipboard the success message wins instead. We accept both because the contract under
    // test is "the user always sees a confirmation", not which branch fires.
    const note = await screen.findByText(/Link copied!|Copy failed/);
    expect(note.textContent).toMatch(/^(Link copied!|Copy failed)$/);
  });
});
