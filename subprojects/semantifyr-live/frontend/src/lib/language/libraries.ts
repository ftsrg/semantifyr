/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { normalizeBaseUrl, type LibrariesResponse } from '@semantifyr/editor-common'

import { createStore, type ReadableStore } from '../stores/store'
import type { LibraryDocument, LibraryKind } from './types'

const BUILTIN_PREFIX = 'file:///workspace/.semantifyr/builtin/'
const SEMANTIC_PREFIX = 'file:///workspace/Library/'

// The SysML standard library is the worker's, served beside it; the page reads it by this prefix
export const SYSML_LIBRARY_PREFIX = 'file:///sysml-library/'

// the served flavor whose session a language's semantic library is copied into
const SEMANTIC_LIBRARY_OF: Readonly<Record<string, string>> = {
  'oxsts-with-gamma-library': 'oxsts-with-gamma-library',
  'oxsts-with-sysmlv2-library': 'oxsts-with-sysmlv2-library',
  'gamma': 'gamma',
  'sysml': 'oxsts-with-sysmlv2-library',
}

// the file each library is known by while the server's listing is not there
const ENTRY_FILES: Readonly<Record<string, readonly { kind: LibraryKind, file: string }[]>> = {
  'oxsts': [
    { kind: 'library', file: 'semantifyr.oxsts' },
  ],
  'oxsts-with-gamma-library': [
    { kind: 'library', file: 'semantifyr.oxsts' },
    { kind: 'semantic-library', file: 'Statecharts.oxsts' },
  ],
  'oxsts-with-sysmlv2-library': [
    { kind: 'library', file: 'semantifyr.oxsts' },
    { kind: 'semantic-library', file: 'States.oxsts' },
  ],
  'gamma': [
    { kind: 'library', file: 'semantifyr.oxsts' },
    { kind: 'semantic-library', file: 'Statecharts.oxsts' },
  ],
  'sysml': [
    { kind: 'standard-library', file: 'Kernel Libraries/Kernel Data Type Library/ScalarValues.kerml' },
    { kind: 'library', file: 'semantifyr.oxsts' },
    { kind: 'semantic-library', file: 'States.oxsts' },
  ],
}

// the kernel first, then the systems library, then the domain libraries and the rest
const STANDARD_LIBRARY_ORDER = [
  'Kernel Libraries/',
  'Systems Library/',
  'Domain Libraries/',
]

function standardRankOf(file: string): number {
  const rank = STANDARD_LIBRARY_ORDER.findIndex((prefix) => {
    return file.startsWith(prefix)
  })
  return rank === -1 ? STANDARD_LIBRARY_ORDER.length : rank
}

function documentOf(kind: LibraryKind, file: string): LibraryDocument {
  switch (kind) {
    case 'library':
      return { kind, uri: `${BUILTIN_PREFIX}${file}`, languageId: 'oxsts' }
    case 'semantic-library':
      return { kind, uri: `${SEMANTIC_PREFIX}${file}`, languageId: 'oxsts' }
    case 'standard-library':
      return { kind, uri: `${SYSML_LIBRARY_PREFIX}${encodeURI(file)}`, languageId: 'sysml' }
  }
}

// every file of every library a session of the flavor has, the standard library first for SysML
export function libraryDocumentsOf(flavorId: string, listing: LibrariesResponse | null): readonly LibraryDocument[] {
  if (!listing) {
    return (ENTRY_FILES[flavorId] ?? []).map(({ kind, file }) => {
      return documentOf(kind, file)
    })
  }
  const semanticFlavor = SEMANTIC_LIBRARY_OF[flavorId]
  const semantic = semanticFlavor === undefined ? [] : listing.semantic[semanticFlavor] ?? []
  const standard = flavorId === 'sysml'
    ? [...listing.sysml].sort((first, second) => {
      return standardRankOf(first) - standardRankOf(second)
    })
    : []
  return [
    ...standard.map((file) => {
      return documentOf('standard-library', file)
    }),
    ...listing.builtin.map((file) => {
      return documentOf('library', file)
    }),
    ...semantic.map((file) => {
      return documentOf('semantic-library', file)
    }),
  ]
}

let listing: Promise<LibrariesResponse | null> | null = null

// the server lists the files of its libraries once per visit; without an answer each library is known by its entry file
function listLibraries(backendUrl: string): Promise<LibrariesResponse | null> {
  listing ??= fetch(`${normalizeBaseUrl(backendUrl).http}/api/libraries`)
    .then(async (response) => {
      return response.ok ? await response.json() as LibrariesResponse : null
    })
    .catch(() => {
      return null
    })
    .then((result) => {
      if (result === null) {
        listing = null
      }
      return result
    })
  return listing
}

export function libraryDocumentsStore(flavorId: string, backendUrl: string): ReadableStore<readonly LibraryDocument[]> {
  const documents = createStore(libraryDocumentsOf(flavorId, null))
  void listLibraries(backendUrl).then((result) => {
    if (result) {
      documents.set(libraryDocumentsOf(flavorId, result))
    }
  })
  return documents
}

export async function readSysmlLibrary(uri: string, origin: string): Promise<string | null> {
  const response = await fetch(`${origin}/sysml-worker/library/${uri.slice(SYSML_LIBRARY_PREFIX.length)}`)
  return response.ok ? response.text() : null
}
