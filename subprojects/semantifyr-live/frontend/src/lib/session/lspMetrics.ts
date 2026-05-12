/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { CancellationToken, LspClient } from '../verification/engine';

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
  let requestCount = 0
  let successCount = 0
  let notificationCount = 0
  let errorCount = 0
  let lastResponseTimeMs: number | null = null
  let totalSuccessResponseTimeMs = 0

  const getMetrics = (): LspMetrics => ({
    requestCount,
    notificationCount,
    errorCount,
    lastResponseTimeMs,
    // Average over successful requests only; errors don't contribute a meaningful elapsed
    // time (the catch path fires before we can measure a useful round-trip), so including
    // them in the denominator dilutes the average without telling the user anything.
    avgResponseTimeMs:
      successCount > 0 ? Math.round(totalSuccessResponseTimeMs / successCount) : null,
  })

  const reset = (): void => {
    requestCount = 0
    successCount = 0
    notificationCount = 0
    errorCount = 0
    lastResponseTimeMs = null
    totalSuccessResponseTimeMs = 0
  }

  return {
    sendRequest: async (method: string, params?: unknown, token?: CancellationToken): Promise<unknown> => {
      const start = performance.now()
      requestCount++
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
            : await client.sendRequest(method, params, token)
        const elapsed = Math.round(performance.now() - start)
        lastResponseTimeMs = elapsed
        totalSuccessResponseTimeMs += elapsed
        successCount++
        return result
      } catch (error) {
        errorCount++
        throw error
      }
    },
    sendNotification: (method: string, params?: unknown): void => {
      notificationCount++
      client.sendNotification(method, params)
    },
    getMetrics,
    reset,
  }
}
