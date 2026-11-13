/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { createMessageConnection } from 'vscode-jsonrpc/node';
import { ConsoleLogger, toSocket, WebSocketMessageReader, WebSocketMessageWriter } from 'vscode-ws-jsonrpc';
import { discoverVerificationCases, verifyCase } from '@semantifyr/editor-common';
import { normalizeBaseUrl } from '@semantifyr/editor-common';
import { createApi } from '../lib/api';

const url = process.env.SEMANTIFYR_LIVE_URL;
if (!url) {
  throw new Error('SEMANTIFYR_LIVE_URL is not set');
}
const api = createApi(url);
const model = readFileSync(new URL('../../../../../oxsts-test-models/smoke/smoke.oxsts', import.meta.url), 'utf-8');

async function connect(flavorId: string) {
  const webSocket = new WebSocket(`${normalizeBaseUrl(api.httpBase).ws}/ws/lsp/${encodeURIComponent(flavorId)}`);
  await new Promise((resolve, reject) => {
    webSocket.onopen = resolve;
    webSocket.onerror = reject;
  });
  const socket = toSocket(webSocket);
  // vscode-ws-jsonrpc's createWebSocketConnection uses a vscode-jsonrpc copy with no Node runtime installed, so it never dispatches a response
  const connection = createMessageConnection(new WebSocketMessageReader(socket), new WebSocketMessageWriter(socket), new ConsoleLogger());
  // the server blocks on its own requests, such as window/workDoneProgress/create, until answered
  connection.onRequest(() => null);
  connection.listen();
  return connection;
}

describe(`live deployment at ${url}`, () => {
  it('serves the health endpoint and the web root', async () => {
    expect((await fetch(`${api.httpBase}/api/health`)).ok).toBe(true);
    expect((await fetch(`${api.httpBase}/`)).ok).toBe(true);
  });

  async function verifySmokeModel(portfolio: string) {
    const flavor = await api.fetchFlavor('oxsts');
    if (!flavor?.discoveryCommand || !flavor.verificationCommand) {
      throw new Error('the oxsts flavor offers no verification');
    }
    const uri = `file:///workspace/${flavor.fileName}`;
    const connection = await connect(flavor.id);
    try {
      await connection.sendRequest('initialize', {
        processId: null,
        rootUri: 'file:///workspace/',
        workspaceFolders: [{ uri: 'file:///workspace/', name: 'workspace' }],
        capabilities: {
          workspace: { workspaceFolders: true, executeCommand: {} },
          window: { workDoneProgress: true },
        },
      });
      await connection.sendNotification('initialized', {});
      await connection.sendNotification('textDocument/didOpen', {
        textDocument: { uri, languageId: flavor.languageId, version: 1, text: model },
      });

      const cases = await discoverVerificationCases(connection, flavor.discoveryCommand, uri);
      expect(cases).not.toHaveLength(0);
      const results = [];
      for (const verificationCase of cases) {
        const result = await verifyCase(
          connection,
          flavor.verificationCommand,
          { uri, range: verificationCase.location.range },
          { portfolio },
        );
        expect(result?.status, `${verificationCase.label}: ${result?.message ?? ''}`).toBe('passed');
        results.push(result);
      }
      return results;
    } finally {
      connection.dispose();
    }
  }

  it('verifies the smoke model over the language server socket', async () => {
    await verifySmokeModel('smart');
  });

  // smart answers with Theta first, so only a Spin portfolio shows that Spin can compile and run a search
  it('verifies the smoke model on Spin alone', async () => {
    const results = await verifySmokeModel('spin-safe-dfs');
    for (const result of results) {
      expect(result?.backendId).toMatch(/^spin:/);
    }
  });
});
