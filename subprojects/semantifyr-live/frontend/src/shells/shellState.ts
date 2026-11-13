/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { VerdictStatus } from '@semantifyr/editor-common/marks'

import type { CursorPosition } from '../lib/session/tabbedEditor'
import { createStore, type Store } from '../lib/stores/store'
import type { Live } from '../lib/workspace/live'

export type CountFilter = Exclude<VerdictStatus, 'not-run' | 'running' | 'queued'> | 'waiting'

// What only the shells show, kept for the visit and never stored
export interface ShellState {
  dismissedOrientations: Store<ReadonlySet<string>>
  // Verify all was pressed while the model had errors
  verifyBlocked: Store<boolean>
  cursor: Store<CursorPosition | null>
  filter: Store<CountFilter | null>
  // the edit control was pressed, so the model takes typing on touch input
  editing: Store<boolean>
  // the trace view chosen per language
  traceViews: Store<ReadonlyMap<string, string>>
  // the width of a fixed embed's results column, null for the even split; a frame differs from page to page, so it is never stored
  embedResultsWidth: Store<number | null>
}

export function createShellState(): ShellState {
  return {
    dismissedOrientations: createStore<ReadonlySet<string>>(new Set()),
    verifyBlocked: createStore(false),
    cursor: createStore<CursorPosition | null>(null),
    filter: createStore<CountFilter | null>(null),
    editing: createStore(false),
    traceViews: createStore<ReadonlyMap<string, string>>(new Map()),
    embedResultsWidth: createStore<number | null>(null),
  }
}

export function errorCount(live: Live): number {
  return live.problems.get().filter((problem) => {
    return problem.severity === 'error'
  }).length
}

export function dismissOrientation(shell: ShellState, key: string): void {
  shell.dismissedOrientations.update((previous) => {
    return new Set([...previous, key])
  })
}

export function verifyAll(live: Live, shell: ShellState): void {
  if (errorCount(live) > 0) {
    shell.verifyBlocked.set(true)
    return
  }
  shell.verifyBlocked.set(false)
  const key = live.workspace.get().documentKey
  if (key !== null) {
    dismissOrientation(shell, key)
  }
  live.verifyAll()
}
