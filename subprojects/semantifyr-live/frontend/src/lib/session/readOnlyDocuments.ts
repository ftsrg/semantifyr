/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Location } from '@semantifyr/editor-common'

// Library files a definition leads into are opened under this scheme, so no language client syncs them
export const LIBRARY_SCHEME = 'live-library'

// Documents the page derives, such as the OXSTS of the model, live under this scheme
export const DERIVED_SCHEME = 'live-derived'

const LIBRARY_PREFIXES = [
  'file:///workspace/.semantifyr/',
  'file:///workspace/Library/',
  'file:///sysml-library/',
]

// the SysML worker names its standard library by the address it loads it from
function workerLibraryPrefix(): string {
  return typeof window === 'undefined' ? 'http://localhost/sysml-worker/library/' : `${window.location.origin}/sysml-worker/library/`
}

const SYSML_LIBRARY_PATH = '/sysml-library/'

export function isLibraryFileUri(uri: string): boolean {
  return uri.startsWith(workerLibraryPrefix()) || LIBRARY_PREFIXES.some((prefix) => {
    return uri.startsWith(prefix)
  })
}

export function libraryUriOf(fileUri: string): string {
  const workerPrefix = workerLibraryPrefix()
  if (fileUri.startsWith(workerPrefix)) {
    return `${LIBRARY_SCHEME}:${SYSML_LIBRARY_PATH}${fileUri.slice(workerPrefix.length)}`
  }
  return `${LIBRARY_SCHEME}:${fileUri.slice('file://'.length)}`
}

export function fileUriOfLibrary(libraryUri: string): string {
  return `file://${libraryUri.slice(`${LIBRARY_SCHEME}:`.length)}`
}

export function derivedUriOf(id: string, fileName: string): string {
  return `${DERIVED_SCHEME}:/${id}/${fileName}`
}

export function fileNameOf(uri: string): string {
  return uri.slice(uri.lastIndexOf('/') + 1)
}

export function toLibraryLocation<T extends Pick<Location, 'uri'>>(location: T): T {
  return isLibraryFileUri(location.uri) ? { ...location, uri: libraryUriOf(location.uri) } : location
}
