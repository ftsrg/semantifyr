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
    // Average over successful requests only; failures don't have a meaningful round-trip.
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
        // Don't forward an undefined token; vscode-jsonrpc serialises it as a positional null
        // and lsp4j rejects the array with "Expected END_ARRAY but was NULL".
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
