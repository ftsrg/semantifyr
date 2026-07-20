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
} from '../lib/verification';
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
    const onProgress = (_: (params: unknown) => void) => () => {};

    runAllVerifications(client, 'foo.case.verify', 'file:///x', [sampleCase('a')], () => {}, onProgress);
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(sendRequest).toHaveBeenCalledTimes(1);
    const callArgs = sendRequest.mock.calls[0]!;
    expect(callArgs[0]).toBe('workspace/executeCommand');
    expect(callArgs[1]).toMatchObject({ command: 'foo.case.verify' });
    expect(VscodeCancellationToken.is(callArgs[2])).toBe(true);
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
        if (typeof next === 'function') next({ phase: 'idle', cases: [] });
      },
      (_) => () => {},
    );
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(sendRequest).toHaveBeenCalledTimes(1);
    const callArgs = sendRequest.mock.calls[0]!;
    expect(VscodeCancellationToken.is(callArgs[2])).toBe(true);
  });

  it('wrapClientWithMetrics forwards two args when no token is supplied (no trailing undefined)', async () => {
    const sendRequest = vi.fn<LspClient['sendRequest']>(async () => null);
    const inner: LspClient = { sendRequest, sendNotification: () => {} };
    const wrapped = wrapClientWithMetrics(inner);
    await wrapped.sendRequest('workspace/executeCommand', { command: 'noop', arguments: [] });
    expect(sendRequest).toHaveBeenCalledTimes(1);
    const callArgs = sendRequest.mock.calls[0]!;
    expect(callArgs.length).toBe(2);
  });

  it('wrapClientWithMetrics forwards three args when a token is supplied', async () => {
    const sendRequest = vi.fn<LspClient['sendRequest']>(async () => null);
    const inner: LspClient = { sendRequest, sendNotification: () => {} };
    const wrapped = wrapClientWithMetrics(inner);
    const source = createCancellationTokenSource();
    await wrapped.sendRequest('workspace/executeCommand', { command: 'noop', arguments: [] }, source.token);
    expect(sendRequest).toHaveBeenCalledTimes(1);
    const callArgs = sendRequest.mock.calls[0]!;
    expect(callArgs.length).toBe(3);
    expect(VscodeCancellationToken.is(callArgs[2])).toBe(true);
  });

  it('verifySingleCase cancel sends window/workDoneProgress/cancel for active progress tokens', async () => {
    const sendNotification = vi.fn();
    const sendRequest = vi.fn<LspClient['sendRequest']>(() => new Promise<unknown>(() => {
      // Never resolves
    }));
    const client: LspClient = { sendRequest, sendNotification };

    const captured: { listener: ((params: unknown) => void) | null } = { listener: null };
    const onProgress = (listener: (params: unknown) => void) => {
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
    const sendRequest = vi.fn<LspClient['sendRequest']>((_method, _params, token) => {
      capturedToken = token;
      return pending;
    });
    const client: LspClient = {
      sendRequest,
      sendNotification: () => {},
    };
    const onProgress = (_: (params: unknown) => void) => () => {};

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
