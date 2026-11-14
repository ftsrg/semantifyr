/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

import { fileNameOf, servedExamples } from '@semantifyr/editor-common/examples'
import { describe, expect, it } from 'vitest'

import { examplePages } from '../../examplePages'

const TEMPLATE = readFileSync(resolve(import.meta.dirname, '../../../index.html'), 'utf-8')

const REUSE = readFileSync(resolve(import.meta.dirname, '../../../../../../REUSE.toml'), 'utf-8')

// the holders and the licences the REUSE file gives, and the addresses of the sources it names, which a page must not carry
const RIGHTS = [
  ...[...REUSE.matchAll(/^SPDX-[\w-]+ = (?:"([^"]+)"|\[([^\]]*)\])/gm)].flatMap((match) => {
    return match[1] !== undefined ? [match[1]] : [...match[2]!.matchAll(/"([^"]+)"/g)].map((value) => {
      return value[1]!
    })
  }),
  ...[...REUSE.matchAll(/https?:\/\/[^\s)"]+/g)].map((match) => {
    return match[0].replace(/\.$/, '')
  }),
]

function commentOf(fileName: string): string {
  return `The comment of ${fileName}.`
}

interface Head {
  title: string
  descriptions: string[]
  text: string
}

function headOf(source: string): Head {
  const page = new DOMParser().parseFromString(source, 'text/html')
  const contents = (selector: string): string[] => {
    return [...page.head.querySelectorAll(selector)].map((meta) => {
      return meta.getAttribute('content') ?? ''
    })
  }
  const descriptions = contents('meta[name="description"], meta[property="og:description"]')
  return {
    title: page.title,
    descriptions,
    text: [page.title, ...descriptions, ...contents('meta[property="og:title"]')].join('\n'),
  }
}

describe('an example\'s page', () => {
  const pages = examplePages(TEMPLATE, commentOf)

  it('is written for every served example at its address', () => {
    expect(pages.map((page) => {
      return page.fileName
    })).toEqual(servedExamples.map((example) => {
      return `${example.language}/${example.id}/index.html`
    }))
  })

  it('names its model in its title and its description, from the registry and the model\'s comment', () => {
    const crossroads = pages.find((page) => {
      return page.fileName === 'sysml/crossroads/index.html'
    })!
    const head = headOf(crossroads.source)

    expect(head.title).toBe('Crossroads - SysML v2 - Semantifyr Live')
    expect(head.descriptions).toEqual([
      'Crossroads, in SysML v2. The comment of crossroads.sysml. Verify it in your browser and read its traces.',
      'Crossroads, in SysML v2. The comment of crossroads.sysml. Verify it in your browser and read its traces.',
    ])
    for (const [index, example] of servedExamples.entries()) {
      const exampleHead = headOf(pages[index]!.source)
      expect(exampleHead.title, example.id).toContain(example.label)
      expect(exampleHead.descriptions, example.id).toEqual([
        expect.stringContaining(commentOf(fileNameOf(example))),
        expect.stringContaining(commentOf(fileNameOf(example))),
      ])
    }
  })

  it('names nothing of a model\'s origin or rights', () => {
    expect(RIGHTS).toContain('The contributors to the Gamma project')
    for (const page of pages) {
      const head = headOf(page.source)
      for (const right of RIGHTS) {
        expect(head.text, page.fileName).not.toContain(right)
      }
      expect(head.text, page.fileName).not.toMatch(/copyright|licen[cs]e|spdx/i)
    }
  })
})
