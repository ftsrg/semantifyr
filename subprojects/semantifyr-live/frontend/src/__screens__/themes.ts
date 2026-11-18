/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { ResolvedColorMode } from '../lib/util/colorMode'

// a state's assertions are the same in both themes but the background, so the dark theme is shown once per shell
export function colorModesOf(firstOfShell: boolean): ResolvedColorMode[] {
  if (firstOfShell) {
    return [
      'light',
      'dark',
    ]
  }
  return [
    'light',
  ]
}
