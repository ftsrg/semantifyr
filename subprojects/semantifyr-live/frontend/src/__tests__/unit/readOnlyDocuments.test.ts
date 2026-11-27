/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it, vi } from 'vitest'
import type { LibrariesResponse } from '@semantifyr/editor-common'
import { libraryDocumentsOf } from '../../lib/language/libraries'

// Goal: the read-only documents offered are every file of the libraries of the language

vi.mock('vscode', () => {
  return {}
})

vi.mock('../../lib/session/monaco', () => {
  return {}
})

vi.mock('monaco-editor', () => {
  return {}
})

const LISTING: LibrariesResponse = {
  builtin: [
    'standard.semantifyr',
  ],
  semantic: {
    'oxsts-with-gamma-library': [
      'Ports.semantifyr',
      'Statecharts.semantifyr',
    ],
    'oxsts-with-sysml-library': [
      'States.semantifyr',
    ],
    'gamma': [
      'Ports.semantifyr',
      'Statecharts.semantifyr',
    ],
  },
  sysml: [
    'Kernel Libraries/Kernel Data Type Library/ScalarValues.kerml',
    'Systems Library/States.sysml',
  ],
}

describe('the library documents of a language', () => {
  it('lists every file of the standard, the builtin and the semantic library of SysML', () => {
    expect(libraryDocumentsOf('sysml', LISTING).map((library) => {
      return `${library.kind} ${library.uri} ${library.languageId}`
    })).toEqual([
      'standard-library file:///sysml-library/Kernel%20Libraries/Kernel%20Data%20Type%20Library/ScalarValues.kerml sysml',
      'standard-library file:///sysml-library/Systems%20Library/States.sysml sysml',
      'library file:///workspace/.semantifyr/builtin/standard.semantifyr oxsts',
      'semantic-library file:///workspace/Library/States.semantifyr oxsts',
    ])
  })

  it('lists the builtin and the Gamma library for Gamma, and the builtin library alone for plain OXSTS', () => {
    expect(libraryDocumentsOf('gamma', LISTING).map((library) => {
      return library.uri
    })).toEqual([
      'file:///workspace/.semantifyr/builtin/standard.semantifyr',
      'file:///workspace/Library/Ports.semantifyr',
      'file:///workspace/Library/Statecharts.semantifyr',
    ])
    expect(libraryDocumentsOf('oxsts', LISTING).map((library) => {
      return library.uri
    })).toEqual([
      'file:///workspace/.semantifyr/builtin/standard.semantifyr',
    ])
  })

  it('offers each library by its entry file while the server lists none', () => {
    expect(libraryDocumentsOf('oxsts-with-gamma-library', null).map((library) => {
      return library.uri
    })).toEqual([
      'file:///workspace/.semantifyr/builtin/standard.semantifyr',
      'file:///workspace/Library/Statecharts.semantifyr',
    ])
  })
})
