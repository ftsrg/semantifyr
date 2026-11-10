/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { LiveEditorView } from '@semantifyr/editor-common'

export type ShellKind =
  | 'desktop'
  | 'embed'
  | 'phone'

// below this width one pane shows at a time; a tablet in portrait, even a small one at 744, keeps the desktop shell
export const PHONE_WIDTH = 700

export const PHONE_QUERY = `(max-width: ${PHONE_WIDTH - 0.02}px)`

// the address chooses the embed, the width the phone; touch input never chooses a shell
export function chooseShell(view: LiveEditorView | null, phoneWidth: boolean): ShellKind {
  if (view !== null) {
    return 'embed'
  }
  return phoneWidth ? 'phone' : 'desktop'
}
