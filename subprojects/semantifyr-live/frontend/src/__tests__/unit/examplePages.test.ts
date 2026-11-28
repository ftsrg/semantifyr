/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

import { liveExampleUrl } from '@semantifyr/editor-common'
import { servedExamples } from '@semantifyr/editor-common/examples'
import { describe, expect, it } from 'vitest'

import { examplePages } from '../../examplePages'

const TEMPLATE = readFileSync(resolve(import.meta.dirname, '../../../index.html'), 'utf-8')

interface Head {
  title: string
  descriptions: string[]
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
  }
}

describe('an example\'s page', () => {
  const pages = examplePages(TEMPLATE)

  it('is written for every served example at its address', () => {
    expect(pages.map((page) => {
      return page.fileName
    })).toEqual(servedExamples.map((example) => {
      return `${new URL(liveExampleUrl('https://live.example/', example.language, example.id)).pathname.slice(1)}/index.html`
    }))
  })

  it('names its model in its title and its description', () => {
    const crossroads = pages.find((page) => {
      return page.fileName === 'sysml/crossroads/index.html'
    })!
    const head = headOf(crossroads.source)

    expect(head.title).toBe('Crossroads - SysML v2 - Semantifyr Live')
    expect(head.descriptions).toEqual([
      'Crossroads, in SysML v2. Two traffic lights at a crossroads, cycled by one controller and interrupted by the police. Verify it in your browser and read its traces.',
      'Crossroads, in SysML v2. Two traffic lights at a crossroads, cycled by one controller and interrupted by the police. Verify it in your browser and read its traces.',
    ])
  })
})
