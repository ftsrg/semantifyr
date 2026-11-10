/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { LiveFlavor } from '../../examples'
import type { LanguageRegistry } from '../language/registry'

export interface DocumentFile {
  name: string
  // null where the browser gives no way back into the file
  write: ((text: string) => Promise<void>) | null
}

export interface OpenedFile extends DocumentFile {
  text: string
}

export interface FileAccess {
  pick: () => Promise<OpenedFile | null>
  // null where the browser has no save dialog
  saveAs: ((suggestedName: string, text: string) => Promise<DocumentFile | null>) | null
  download: (name: string, text: string) => void
  confirmDiscard: (name: string) => boolean
}

interface WritableStream {
  write: (text: string) => Promise<void>
  close: () => Promise<void>
}

interface FileHandle {
  kind: string
  name: string
  getFile: () => Promise<File>
  createWritable: () => Promise<WritableStream>
}

interface FilePickerWindow {
  showOpenFilePicker?: (options: { types: { description: string, accept: Record<string, string[]> }[] }) => Promise<FileHandle[]>
  showSaveFilePicker?: (options: { suggestedName: string, types: { description: string, accept: Record<string, string[]> }[] }) => Promise<FileHandle>
}

interface HandleItem {
  getAsFileSystemHandle?: () => Promise<FileHandle | null>
}

const EXTENSIONS_OF_LANGUAGE: Readonly<Record<string, readonly string[]>> = {
  sysml: ['.sysml', '.kerml'],
  gamma: ['.gamma'],
  oxsts: ['.oxsts'],
}

const MODEL_EXTENSIONS = Object.values(EXTENSIONS_OF_LANGUAGE).flat()

const FILE_TYPES = [
  { description: 'Models', accept: { 'text/plain': MODEL_EXTENSIONS } },
]

export function languageOfFile(fileName: string): string | null {
  const lower = fileName.toLowerCase()
  return Object.entries(EXTENSIONS_OF_LANGUAGE).find(([, extensions]) => {
    return extensions.some((extension) => {
      return lower.endsWith(extension)
    })
  })?.[0] ?? null
}

// a file of an extension no language has opens in the current one
export function flavorOfFile(registry: LanguageRegistry, fileName: string, current: LiveFlavor | null): LiveFlavor {
  const languageId = languageOfFile(fileName)
  const flavor = languageId === 'oxsts'
    ? registry.flavorOf('oxsts')
    : registry.units.find((unit) => {
      return unit.id === languageId
    })?.flavors[0]
  return flavor ?? current ?? registry.flavors[0]
}

function isAbort(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}

function writerOf(handle: FileHandle): (text: string) => Promise<void> {
  return async (text) => {
    const stream = await handle.createWritable()
    await stream.write(text)
    await stream.close()
  }
}

async function openedFromHandle(handle: FileHandle): Promise<OpenedFile> {
  const file = await handle.getFile()
  return { name: handle.name, text: await file.text(), write: writerOf(handle) }
}

async function openedFromFile(file: File): Promise<OpenedFile> {
  return { name: file.name, text: await file.text(), write: null }
}

function pickWithInput(): Promise<OpenedFile | null> {
  return new Promise((resolve) => {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = MODEL_EXTENSIONS.join(',')
    input.addEventListener('change', () => {
      const file = input.files?.[0]
      resolve(file ? openedFromFile(file) : null)
    })
    input.addEventListener('cancel', () => {
      resolve(null)
    })
    input.click()
  })
}

export async function fileOfDrop(transfer: DataTransfer): Promise<OpenedFile | null> {
  const item = transfer.items[0]
  if (item?.kind !== 'file') {
    return null
  }
  const file = item.getAsFile()
  const handle = await (item as unknown as HandleItem).getAsFileSystemHandle?.()
  if (handle?.kind === 'file') {
    return openedFromHandle(handle)
  }
  return file ? openedFromFile(file) : null
}

export function browserFileAccess(): FileAccess {
  const picker = window as unknown as FilePickerWindow
  const { showOpenFilePicker, showSaveFilePicker } = picker
  return {
    pick: async () => {
      if (!showOpenFilePicker) {
        return pickWithInput()
      }
      try {
        const [handle] = await showOpenFilePicker.call(window, { types: FILE_TYPES })
        return handle ? await openedFromHandle(handle) : null
      } catch (error) {
        if (isAbort(error)) {
          return null
        }
        throw error
      }
    },
    saveAs: showSaveFilePicker
      ? async (suggestedName, text) => {
        try {
          const handle = await showSaveFilePicker.call(window, { suggestedName, types: FILE_TYPES })
          const write = writerOf(handle)
          await write(text)
          return { name: handle.name, write }
        } catch (error) {
          if (isAbort(error)) {
            return null
          }
          throw error
        }
      }
      : null,
    download: (name, text) => {
      const address = URL.createObjectURL(new Blob([text], { type: 'text/plain' }))
      const anchor = document.createElement('a')
      anchor.href = address
      anchor.download = name
      anchor.click()
      URL.revokeObjectURL(address)
    },
    confirmDiscard: (name) => {
      return window.confirm(`Open the file and discard the unsaved changes of ${name}?`)
    },
  }
}
