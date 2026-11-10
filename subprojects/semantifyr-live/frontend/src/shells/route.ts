/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { SessionKind } from '../lib/workspace/live'

// The new shells are served under this path beside today's page until the default switches
export const SHELL_ROUTE = '/next'

export function isShellRoute(pathname: string): boolean {
  return pathname === SHELL_ROUTE || pathname.startsWith(`${SHELL_ROUTE}/`)
}

export type ShellKind =
  | 'desktop'
  | 'embed'
  | 'phone'

export type EmbedView =
  | 'verify'
  | 'edit-only'

// below this width one pane shows at a time; a tablet in portrait, even a small one at 744, keeps the desktop shell
export const PHONE_WIDTH = 700

export const PHONE_QUERY = `(max-width: ${PHONE_WIDTH - 0.02}px)`

// embed=1 keeps meaning the verifying view; embed=edit is the edit-only view
export function embedViewOf(search: string): EmbedView | null {
  switch (new URLSearchParams(search).get('embed')) {
    case '1':
      return 'verify'
    case 'edit':
      return 'edit-only'
    default:
      return null
  }
}

export function sessionKindOf(search: string): SessionKind {
  switch (embedViewOf(search)) {
    case 'verify':
      return 'embed'
    case 'edit-only':
      return 'edit-only'
    case null:
      return 'full'
  }
}

// the address chooses the embed, the width the phone; touch input never chooses a shell
export function chooseShell(search: string, phoneWidth: boolean): ShellKind {
  if (embedViewOf(search) !== null) {
    return 'embed'
  }
  return phoneWidth ? 'phone' : 'desktop'
}
