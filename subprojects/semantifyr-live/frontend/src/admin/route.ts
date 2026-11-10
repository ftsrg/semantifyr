/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

export function isAdminRoute(pathname: string): boolean {
  return pathname === '/admin' || pathname === '/admin/'
}
