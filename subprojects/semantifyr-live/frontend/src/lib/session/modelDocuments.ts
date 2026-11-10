/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

export interface ModelChange {
  // the whole text was replaced by a switch of the document, not edited
  replaced: boolean
}

// The editor's text models by URI, whatever the editor shows
export interface ModelDocuments {
  isOpen: (uri: string) => boolean
  read: (uri: string) => string | null
  replace: (uri: string, text: string) => void
  onDidOpen: (uri: string, listener: () => void) => () => void
  onDidChange: (uri: string, listener: (change: ModelChange) => void) => () => void
}
