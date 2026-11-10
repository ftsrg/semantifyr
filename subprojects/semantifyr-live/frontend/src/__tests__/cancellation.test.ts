/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it, vi } from 'vitest';
import { CancellationToken as VscodeCancellationToken } from 'vscode-jsonrpc';
import {
  createCancellationTokenSource,
  runAllVerifications,
  verifySingleCase,
  type LspClient,
} from '../lib/verification/engine';
import { wrapClientWithMetrics } from '../lib/session/lspMetrics';
import { sampleCase } from './fixtures/cases';

describe('LSP cancellation plumbing', () => {
  it('createCancellationTokenSource produces a token that vscode-jsonrpc recognises', () => {
    const source = createCancellationTokenSource();
    expect(VscodeCancellationToken.is(source.token)).toBe(true);
  });

  it('runAllVerifications passes a recognizable token as the third argument to sendRequest', async () => {
    const sendRequest = vi.fn<LspClient['sendRequest']>(async () => null);
    const client: LspClient = {
      sendRequest,
      sendNotification: () => {},
    };
    const onProgress = (_: (parameters: unknown) => void) => () => {};

    runAllVerifications(client, 'foo.case.verify', 'file:///x', [sampleCase('a')], () => {}, onProgress);
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(sendRequest).toHaveBeenCalledTimes(1);
    const callArguments = sendRequest.mock.calls[0]!;
    expect(callArguments[0]).toBe('workspace/executeCommand');
    expect(callArguments[1]).toMatchObject({ command: 'foo.case.verify' });
    expect(VscodeCancellationToken.is(callArguments[2])).toBe(true);
  });

  it('verifySingleCase passes a recognizable token as the third argument to sendRequest', async () => {
    const sendRequest = vi.fn<LspClient['sendRequest']>(async () => null);
    const client: LspClient = {
      sendRequest,
      sendNotification: () => {},
    };

    verifySingleCase(
      client,
      'foo.case.verify',
      'file:///x',
      sampleCase('b'),
      (next) => {
        if (typeof next === 'function') next({ phase: 'idle', discovery: 'idle', cases: [] });
      },
      (_) => () => {},
    );
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(sendRequest).toHaveBeenCalledTimes(1);
    const callArguments = sendRequest.mock.calls[0]!;
    expect(VscodeCancellationToken.is(callArguments[2])).toBe(true);
  });

  it('wrapClientWithMetrics forwards two args when no token is supplied (no trailing undefined)', async () => {
    const sendRequest = vi.fn<LspClient['sendRequest']>(async () => null);
    const inner: LspClient = { sendRequest, sendNotification: () => {} };
    const wrapped = wrapClientWithMetrics(inner);
    await wrapped.sendRequest('workspace/executeCommand', { command: 'noop', arguments: [] });
    expect(sendRequest).toHaveBeenCalledTimes(1);
    const callArguments = sendRequest.mock.calls[0]!;
    expect(callArguments.length).toBe(2);
  });

  it('wrapClientWithMetrics forwards three args when a token is supplied', async () => {
    const sendRequest = vi.fn<LspClient['sendRequest']>(async () => null);
    const inner: LspClient = { sendRequest, sendNotification: () => {} };
    const wrapped = wrapClientWithMetrics(inner);
    const source = createCancellationTokenSource();
    await wrapped.sendRequest('workspace/executeCommand', { command: 'noop', arguments: [] }, source.token);
    expect(sendRequest).toHaveBeenCalledTimes(1);
    const callArguments = sendRequest.mock.calls[0]!;
    expect(callArguments.length).toBe(3);
    expect(VscodeCancellationToken.is(callArguments[2])).toBe(true);
  });

  it('verifySingleCase cancel sends window/workDoneProgress/cancel for active progress tokens', async () => {
    const sendNotification = vi.fn();
    const sendRequest = vi.fn<LspClient['sendRequest']>(() => new Promise<unknown>(() => {
      // Never resolves
    }));
    const client: LspClient = { sendRequest, sendNotification };

    const captured: { listener: ((parameters: unknown) => void) | null } = { listener: null };
    const onProgress = (listener: (parameters: unknown) => void) => {
      captured.listener = listener;
      return () => { captured.listener = null };
    };

    const handle = verifySingleCase(
      client,
      'foo.case.verify',
      'file:///x',
      sampleCase('p'),
      () => {},
      onProgress,
    );
    await new Promise((resolve) => setTimeout(resolve, 0));

    captured.listener?.({ token: 'tok-1', value: { kind: 'begin', title: 'Verifying' } });

    handle.cancel();
    expect(sendNotification).toHaveBeenCalledWith('window/workDoneProgress/cancel', { token: 'tok-1' });
  });

  it('cancelling the run handle flips the token before the request resolves', async () => {
    let capturedToken: unknown = null;
    const pending = new Promise<unknown>(() => {
      // Never resolves
    });
    const sendRequest = vi.fn<LspClient['sendRequest']>((_method, _parameters, token) => {
      capturedToken = token;
      return pending;
    });
    const client: LspClient = {
      sendRequest,
      sendNotification: () => {},
    };
    const onProgress = (_: (parameters: unknown) => void) => () => {};

    const runHandle = runAllVerifications(
      client,
      'foo.case.verify',
      'file:///x',
      [sampleCase('c')],
      () => {},
      onProgress,
    );
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect((capturedToken as { isCancellationRequested?: boolean }).isCancellationRequested).toBe(false);
    runHandle.cancel();
    expect((capturedToken as { isCancellationRequested?: boolean }).isCancellationRequested).toBe(true);
  });
});
