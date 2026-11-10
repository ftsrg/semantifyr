/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

// The rewritten admin page is served under this path beside today's until the default switches
export const ADMIN_ROUTE = '/admin/next'

export function isAdminRoute(pathname: string): boolean {
  return pathname === ADMIN_ROUTE || pathname === `${ADMIN_ROUTE}/`
}
