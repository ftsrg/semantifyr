/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { FileAccess, OpenedFile } from './files'
import type { DocumentRecord, Live } from './live'

export type SaveOutcome =
  | 'written'
  | 'saved'
  | 'downloaded'
  | 'cancelled'

function currentRecord(live: Live): DocumentRecord | undefined {
  const { documentKey } = live.workspace.get()
  return documentKey === null ? undefined : live.documents.get().get(documentKey)
}

export function openFileInto(live: Live, access: FileAccess, opened: OpenedFile): void {
  const record = currentRecord(live)
  if (record?.shared && live.unsaved.get() && !access.confirmDiscard(record.file?.name ?? 'the shared model')) {
    return
  }
  live.openFile(opened)
}

export async function saveDocument(live: Live, access: FileAccess, suggestedName: string): Promise<SaveOutcome> {
  const record = currentRecord(live)
  const text = live.currentText()
  if (!record || text === null) {
    return 'cancelled'
  }
  if (record.file?.write) {
    try {
      await record.file.write(text)
      live.markSaved(null, text)
      return 'written'
    } catch {
      /* the browser refused the write: the file is saved the way a new one is */
    }
  }
  const name = record.file?.name ?? suggestedName
  if (access.saveAs) {
    const file = await access.saveAs(name, text)
    if (file === null) {
      return 'cancelled'
    }
    live.markSaved(file, text)
    return 'saved'
  }
  access.download(name, text)
  live.markSaved(null, text)
  return 'downloaded'
}
