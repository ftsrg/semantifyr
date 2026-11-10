/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Range } from '@semantifyr/editor-common'

import { createStore, type ReadableStore } from '../stores/store'

export interface ReadOnlyDocument {
  // stable per document: opening the same id again shows the open tab
  id: string
  label: string
  uri: string
  languageId: string
  // the line under the tabs that says what the document is
  banner: string
  // the text the page owns; absent, the editor reads the document by its URI
  text?: string | undefined
}

export interface TabSelection {
  id: string
  range: Range
  sequence: number
}

export interface DocumentTabsState {
  tabs: readonly ReadOnlyDocument[]
  // the read-only tab shown, null for the model
  active: string | null
  // a place to show in a tab once it is active
  selection: TabSelection | null
}

export interface DocumentTabs {
  readonly state: ReadableStore<DocumentTabsState>
  // opens the document as a read-only tab beside the model, or shows and updates it when open
  open: (document: ReadOnlyDocument, range?: Range) => void
  // updates the text of an open tab without showing it
  update: (id: string, text: string | undefined) => void
  close: (id: string) => void
  show: (id: string | null) => void
  closeAll: () => void
}

const EMPTY: DocumentTabsState = { tabs: [], active: null, selection: null }

export function createDocumentTabs(): DocumentTabs {
  const state = createStore<DocumentTabsState>(EMPTY)
  let sequence = 0

  return {
    state,
    open: (document, range) => {
      state.update((previous) => {
        const exists = previous.tabs.some((tab) => {
          return tab.id === document.id
        })
        const tabs = exists
          ? previous.tabs.map((tab) => {
            return tab.id === document.id ? document : tab
          })
          : [...previous.tabs, document]
        const selection = range ? { id: document.id, range, sequence: ++sequence } : previous.selection
        return { tabs, active: document.id, selection }
      })
    },
    update: (id, text) => {
      state.update((previous) => {
        const tab = previous.tabs.find((candidate) => {
          return candidate.id === id
        })
        if (!tab || tab.text === text) {
          return previous
        }
        return {
          ...previous,
          tabs: previous.tabs.map((candidate) => {
            return candidate === tab ? { ...tab, text } : candidate
          }),
        }
      })
    },
    close: (id) => {
      state.update((previous) => {
        const tabs = previous.tabs.filter((tab) => {
          return tab.id !== id
        })
        if (tabs.length === previous.tabs.length) {
          return previous
        }
        return { ...previous, tabs, active: previous.active === id ? null : previous.active }
      })
    },
    show: (id) => {
      state.update((previous) => {
        if (previous.active === id) {
          return previous
        }
        const known = id === null || previous.tabs.some((tab) => {
          return tab.id === id
        })
        return known ? { ...previous, active: id } : previous
      })
    },
    closeAll: () => {
      state.set(EMPTY)
    },
  }
}
