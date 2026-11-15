/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it, vi } from 'vitest'

import { flavorOfFile, languageOfFile, OXSTS_EXTENSIONS } from '../../lib/workspace/files'
import { fakeRegistry, testFlavors } from '../helpers/live'

vi.mock('vscode', () => {
  return {}
})

vi.mock('../../lib/session/monaco', () => {
  return {}
})

const grammarWorkflow = readFileSync(resolve('../../oxsts.lang/src/main/java/hu/bme/mit/semantifyr/oxsts/lang/GenerateOxsts.mwe2'), 'utf-8')
const languageExtensions = /fileExtensions = "([^"]+)"/.exec(grammarWorkflow)![1]!.split(',')

describe('the extensions of the OXSTS language', () => {
  it('are those of the grammar workflow, the primary one first', () => {
    expect(OXSTS_EXTENSIONS).toEqual(languageExtensions.map((extension) => {
      return `.${extension}`
    }))
  })

  it.each(languageExtensions)('open a file of .%s in OXSTS', (extension) => {
    const registry = fakeRegistry(testFlavors(), () => {
      throw new Error('no runtime is started')
    })

    expect(languageOfFile(`Model.${extension}`)).toBe('oxsts')
    expect(flavorOfFile(registry, `Model.${extension}`, null).id).toBe('oxsts')
  })
})

describe('the file a document opens in', () => {
  const registry = fakeRegistry(testFlavors(), () => {
    throw new Error('no runtime is started')
  })

  it('is chosen by the extension', () => {
    expect(languageOfFile('model.sysml')).toBe('sysml')
    expect(languageOfFile('Library.KerML')).toBe('sysml')
    expect(languageOfFile('LevelCrossing.gamma')).toBe('gamma')
    expect(languageOfFile('LevelCrossing.get')).toBe('gamma')
    expect(languageOfFile('snippet.oxsts')).toBe('oxsts')
    expect(languageOfFile('notes.txt')).toBeNull()
  })

  it('is the language of the extension, plain OXSTS for .oxsts', () => {
    expect(flavorOfFile(registry, 'a.sysml', null).languageId).toBe('sysml')
    expect(flavorOfFile(registry, 'a.gamma', null).languageId).toBe('gamma')
    expect(flavorOfFile(registry, 'a.oxsts', null).id).toBe('oxsts')
  })

  it('is the current language for a file of another extension', () => {
    const gamma = registry.flavorOf('gamma')!

    expect(flavorOfFile(registry, 'notes.txt', gamma).id).toBe('gamma')
  })
})
