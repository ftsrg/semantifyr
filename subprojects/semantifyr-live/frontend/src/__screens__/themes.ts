/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { ResolvedColorMode } from '../lib/util/colorMode'

export function colorModes(firstStateOfShell: boolean): ResolvedColorMode[] {
  if (firstStateOfShell) {
    return [
      'light',
      'dark',
    ]
  }
  return [
    'light',
  ]
}
