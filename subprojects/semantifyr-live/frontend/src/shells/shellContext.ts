/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { createContext, useContext } from 'react'

import type { ShellState } from './shellState'

export const ShellContext = createContext<ShellState | null>(null)

export function useShell(): ShellState {
  const shell = useContext(ShellContext)
  if (!shell) {
    throw new Error('useShell needs a ShellContext provider')
  }
  return shell
}
