/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

// Library files a definition leads into are opened under this scheme, so no language client syncs them
export const LIBRARY_SCHEME = 'live-library'

// Documents the page derives, such as the OXSTS of the model, live under this scheme
export const DERIVED_SCHEME = 'live-derived'

// A case's trace text and the OXSTS of its trace, named by the path of the file the verification wrote
export const TRACE_SCHEME = 'live-trace'

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

// the address a language server knows a library file by: the session's file, or the worker's for the SysML standard library
export function serverUriOfLibrary(libraryUri: string): string {
  const path = libraryUri.slice(`${LIBRARY_SCHEME}:`.length)
  if (path.startsWith(SYSML_LIBRARY_PATH)) {
    return `${workerLibraryPrefix()}${path.slice(SYSML_LIBRARY_PATH.length)}`
  }
  return `file://${path}`
}

export function derivedUriOf(id: string, fileName: string): string {
  return `${DERIVED_SCHEME}:/${id}/${fileName}`
}

export function traceUriOf(fileUri: string): string {
  return `${TRACE_SCHEME}:${fileUri.slice('file://'.length)}`
}

export function fileNameOf(uri: string): string {
  return uri.slice(uri.lastIndexOf('/') + 1)
}
