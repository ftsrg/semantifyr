/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { CancellationToken, LspClient } from './verification';

export interface LspMetrics {
  requestCount: number;
  notificationCount: number;
  errorCount: number;
  lastResponseTimeMs: number | null;
  avgResponseTimeMs: number | null;
}

export interface MetricsTracker {
  getMetrics: () => LspMetrics;
  reset: () => void;
}

export function wrapClientWithMetrics(client: LspClient): LspClient & MetricsTracker {
  let requestCount = 0;
  let notificationCount = 0;
  let errorCount = 0;
  let lastResponseTimeMs: number | null = null;
  let totalResponseTimeMs = 0;

  const getMetrics = (): LspMetrics => ({
    requestCount,
    notificationCount,
    errorCount,
    lastResponseTimeMs,
    avgResponseTimeMs: requestCount > 0 ? Math.round(totalResponseTimeMs / requestCount) : null,
  });

  const reset = (): void => {
    requestCount = 0;
    notificationCount = 0;
    errorCount = 0;
    lastResponseTimeMs = null;
    totalResponseTimeMs = 0;
  };

  return {
    sendRequest: async (method: string, params?: unknown, token?: CancellationToken): Promise<unknown> => {
      const start = performance.now();
      try {
        // Critical: only forward the token when the caller actually supplied one. The
        // underlying language client's sendRequest is variadic and routes through
        // vscode-jsonrpc's `connection.sendRequest`, which detects a CancellationToken in the
        // last positional argument. Always forwarding three args means an undefined trailing
        // arg falls through that detection and ends up serialised as `null` in a positional
        // params array (`[<payload>, null]`), which lsp4j on the server side rejects with
        // "Expected END_ARRAY but was NULL".
        const result =
          token === undefined
            ? await client.sendRequest(method, params)
            : await client.sendRequest(method, params, token);
        const elapsed = Math.round(performance.now() - start);
        lastResponseTimeMs = elapsed;
        totalResponseTimeMs += elapsed;
        requestCount++;
        return result;
      } catch (error) {
        errorCount++;
        requestCount++;
        throw error;
      }
    },
    sendNotification: (method: string, params?: unknown): void => {
      notificationCount++;
      client.sendNotification(method, params);
    },
    getMetrics,
    reset,
  };
}
