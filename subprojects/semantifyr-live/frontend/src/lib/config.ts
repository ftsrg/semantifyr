/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
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
