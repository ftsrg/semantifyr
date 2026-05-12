/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

/**
 * Resolve the live-server URL the SPA should talk to. Order of precedence:
 * 1. {@code ?backend=} URL parameter (one-off override for the current session).
 * 2. {@code VITE_BACKEND_URL} build-time env (CI / preview deploys).
 * 3. The current page origin (production: SPA + backend served from the same host).
 * 4. {@code http://localhost:18080} (SSR / test fallback).
 */
export function resolveBackendUrl(): string {
  if (typeof window !== 'undefined') {
    const params = new URLSearchParams(window.location.search);
    const fromQuery = params.get('backend');
    if (fromQuery && fromQuery.length > 0) return fromQuery;
  }

  const fromEnv = import.meta.env?.VITE_BACKEND_URL as string | undefined;
  if (fromEnv && fromEnv.length > 0) return fromEnv;

  if (typeof window !== 'undefined') {
    return window.location.origin;
  }

  return 'http://localhost:18080';
}
