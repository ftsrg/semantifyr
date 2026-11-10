/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { LibraryDocument } from './types'

const BUILTIN_LIBRARY: LibraryDocument = {
  kind: 'library',
  uri: 'file:///workspace/.semantifyr/builtin/semantifyr.oxsts',
  languageId: 'oxsts',
}

const GAMMA_SEMANTIC_LIBRARY: LibraryDocument = {
  kind: 'semantic-library',
  uri: 'file:///workspace/Library/Statecharts.oxsts',
  languageId: 'oxsts',
}

const SYSML_SEMANTIC_LIBRARY: LibraryDocument = {
  kind: 'semantic-library',
  uri: 'file:///workspace/Library/States.oxsts',
  languageId: 'oxsts',
}

// The SysML standard library is the worker's, served beside it; the page reads it by this prefix
export const SYSML_LIBRARY_PREFIX = 'file:///sysml-library/'

const SYSML_STANDARD_LIBRARY: LibraryDocument = {
  kind: 'library',
  uri: `${SYSML_LIBRARY_PREFIX}${encodeURI('Kernel Libraries/Kernel Data Type Library/ScalarValues.kerml')}`,
  languageId: 'sysml',
}

// the file each library entry opens at; a definition into a library opens the file it leads to
const LIBRARIES: Readonly<Record<string, readonly LibraryDocument[]>> = {
  'oxsts': [
    BUILTIN_LIBRARY,
  ],
  'oxsts-with-gamma-library': [
    BUILTIN_LIBRARY,
    GAMMA_SEMANTIC_LIBRARY,
  ],
  'oxsts-with-sysmlv2-library': [
    BUILTIN_LIBRARY,
    SYSML_SEMANTIC_LIBRARY,
  ],
  'gamma': [
    GAMMA_SEMANTIC_LIBRARY,
  ],
  'sysml': [
    SYSML_STANDARD_LIBRARY,
    SYSML_SEMANTIC_LIBRARY,
  ],
}

export function librariesOf(flavorId: string): readonly LibraryDocument[] {
  return LIBRARIES[flavorId] ?? []
}

export async function readSysmlLibrary(uri: string, origin: string): Promise<string | null> {
  const response = await fetch(`${origin}/sysml-worker/library/${uri.slice(SYSML_LIBRARY_PREFIX.length)}`)
  return response.ok ? response.text() : null
}
