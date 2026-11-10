/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

// The new shells are served under this path beside today's page until the default switches
export const SHELL_ROUTE = '/next'

export function isShellRoute(pathname: string): boolean {
  return pathname === SHELL_ROUTE || pathname.startsWith(`${SHELL_ROUTE}/`)
}

export type ShellKind =
  | 'desktop'

// the embed and the phone shell join here, chosen by the address and the width
export function chooseShell(): ShellKind {
  return 'desktop'
}
