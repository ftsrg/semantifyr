/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it, vi } from 'vitest'
import { encodeCompressedBase64Url, liveEditorUrl } from '@semantifyr/editor-common'

import { findExample } from '../../examples'
import type { LspClient } from '../../lib/verification/engine'
import { fixedFrame, frameColorMode, historyChangeOf, readAddress, resolveAddress } from '../../lib/workspace/address'
import { fakeRegistry, fakeRuntime, testFlavors } from '../helpers/live'

vi.mock('vscode', () => {
  return {}
})

vi.mock('../../lib/session/monaco', () => {
  return {}
})

// Goal: the path names the model, the query its flavour, the fragment what the visitor made; an unknown name says what opened instead

const silent: LspClient = {
  sendRequest: async () => {
    return null
  },
  sendNotification: () => {},
}

async function resolved(address: string) {
  const registry = fakeRegistry(testFlavors(), (flavor) => {
    return fakeRuntime(flavor, silent)
  })
  return resolveAddress(readAddress(address), registry, 'sysml')
}

describe('an address', () => {
  it('opens SysML v2 with the level crossing when it names nothing', async () => {
    const opened = await resolved('/')

    expect(opened.flavor.id).toBe('sysml')
    expect(opened.example.id).toBe('level-crossing')
    expect(opened.problem).toBeNull()
  })

  it('opens the language and the example its path names', async () => {
    expect(await resolved('/gamma/crossroads')).toMatchObject({ flavor: { id: 'gamma' }, example: { id: 'crossroads' }, shared: false, problem: null })
    expect(await resolved('/oxsts')).toMatchObject({ flavor: { id: 'oxsts' }, example: { id: 'one-light' } })
  })

  it('opens the flavour its library names, the example keeping its plain name', async () => {
    expect(await resolved('/oxsts/crossroads?library=gamma')).toMatchObject({ flavor: { id: 'oxsts-with-gamma-library' }, example: { id: 'crossroads' }, problem: null })
    expect(await resolved('/oxsts/level-crossing?library=sysml')).toMatchObject({ flavor: { id: 'oxsts-with-sysmlv2-library' }, example: { id: 'level-crossing' } })
  })

  it('says which name it did not find and what it opened in its place', async () => {
    expect(await resolved('/kerml/level-crossing')).toMatchObject({ flavor: { id: 'sysml' }, example: { id: 'level-crossing' }, problem: { kind: 'language', requested: 'kerml' } })
    expect(await resolved('/oxsts/crossroads?library=uml')).toMatchObject({ flavor: { id: 'oxsts' }, example: { id: 'one-light' }, problem: { kind: 'library', requested: 'uml' } })
    expect(await resolved('/gamma/level-crosing')).toMatchObject({ flavor: { id: 'gamma' }, example: { id: 'level-crossing' }, problem: { kind: 'example', requested: 'level-crosing' } })
  })

  it('takes a path longer than a language and an example for an unknown example', async () => {
    expect(await resolved('/sysml/level-crossing/extra')).toMatchObject({ flavor: { id: 'sysml' }, example: { id: 'level-crossing' }, problem: { kind: 'example', requested: 'level-crossing/extra' } })
  })

  it('is a history entry of its own for a switch to an example, and takes the entry of a file or a shared model', () => {
    expect(historyChangeOf('https://live.example/sysml/level-crossing', 'https://live.example/gamma/level-crossing', false)).toBe('push')
    expect(historyChangeOf('https://live.example/sysml/level-crossing', 'https://live.example/sysml', true)).toBe('replace')
    expect(historyChangeOf('https://live.example/sysml/level-crossing#case=a', 'https://live.example/sysml/level-crossing', false)).toBeNull()
  })

  it('gives only an embedded frame a fixed size', () => {
    expect(fixedFrame(readAddress('/gamma/level-crossing?view=embed&frame=fixed'))).toBe(true)
    expect(fixedFrame(readAddress('/oxsts?view=edit-only&frame=fixed'))).toBe(true)
    expect(fixedFrame(readAddress('/oxsts?frame=fixed'))).toBe(false)
    expect(fixedFrame(readAddress('/oxsts?view=embed'))).toBe(false)
  })

  it('gives only an embedded frame the colour mode of its page', () => {
    expect(frameColorMode(readAddress('/oxsts?view=embed&theme=dark'))).toBe('dark')
    expect(frameColorMode(readAddress('/oxsts?view=edit-only&theme=light'))).toBe('light')
    expect(frameColorMode(readAddress('/oxsts?theme=dark'))).toBeNull()
    expect(frameColorMode(readAddress('/oxsts?view=embed'))).toBeNull()
  })

  it('does not read today\'s parameters', async () => {
    expect(await resolved('/?mode=gamma&example=crossroads&code=abc&embed=1')).toMatchObject({ flavor: { id: 'sysml' }, example: { id: 'level-crossing' }, shared: false, problem: null })
  })

  it('opens the model of its fragment as a shared model, or as an edit of the example its path names', async () => {
    const model = await encodeCompressedBase64Url('package edited')

    expect(await resolved(`/oxsts#model=${model}`)).toMatchObject({ flavor: { id: 'oxsts' }, text: 'package edited', shared: true, replace: true })
    expect(await resolved(`/oxsts/hierarchy#model=${model}`)).toMatchObject({ example: { id: 'hierarchy' }, text: 'package edited', shared: false })
    expect(await resolved('/oxsts#model=garbled')).toMatchObject({ shared: false, text: findExample(testFlavors()[0], 'one-light')?.code, problem: { kind: 'model' } })
  })

  it('carries the selected case and the verdicts apart, and the case alone when there are no verdicts', async () => {
    const verdicts = await encodeCompressedBase64Url(JSON.stringify([['a', 'failed']]))

    expect((await resolved(`/oxsts#case=a&verdicts=${verdicts}`)).results).toEqual({ selectedCase: 'a', verdicts: new Map([['a', 'failed']]) })
    expect(await resolved('/oxsts#case=b')).toMatchObject({ selectedCase: 'b' })
    expect(await resolved('/oxsts#verdicts=garbled')).toMatchObject({ problem: { kind: 'results' } })
  })

  it('is what a share link of an untouched example with its results builds', async () => {
    const link = await liveEditorUrl('https://live.semantifyr.org/', { language: 'oxsts', example: 'crossroads', library: 'gamma', case: 'a', verdicts: [['a', 'passed']] })

    expect(await resolved(link)).toMatchObject({ flavor: { id: 'oxsts-with-gamma-library' }, example: { id: 'crossroads' }, shared: false, results: { selectedCase: 'a' } })
  })
})
