/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { encodeCompressedBase64Url } from '@semantifyr/editor-common'
import type { Page } from 'playwright'
import { describe, expect } from 'vitest'

import { casesListed, editor, test } from './harness'

function pathOf(page: Page): string {
  const url = new URL(page.url())
  return `${url.pathname}${url.search}`
}

async function chooseExample(page: Page, label: string): Promise<void> {
  await page.locator('.example-button').click()
  await page.getByRole('menuitem', { name: new RegExp(`^${label}`) }).click()
}

describe('the first screen', () => {
  test('opens the level crossing in SysML v2 on the bare address, with a SysML session', async ({ ui }) => {
    const page = await ui.open('/')

    await expect.poll(async () => {
      return page.title()
    }).toBe('Level crossing - SysML v2 - Semantifyr Live')
    const session = await ui.backend.openSession()
    expect(session.query.get('language')).toBe('sysml')
    expect(session.query.get('kind')).toBe('full')
    expect(pathOf(page)).toBe('/')
    await casesListed(page)
  })

  test('opens the language, the flavour and the example the address names', async ({ ui }) => {
    const page = await ui.open('/oxsts/crossroads?library=gamma')

    await expect.poll(async () => {
      return page.title()
    }).toBe('Crossroads (Gamma library) - OXSTS - Semantifyr Live')
    expect((await ui.backend.openSession()).flavorId).toBe('oxsts-with-gamma-library')
    await expect.poll(async () => {
      return page.locator('.flavour-button').innerText()
    }).toContain('Gamma library')
    await casesListed(page)
  })
})

describe('an address that names what Live does not have', () => {
  for (const [address, requested, opened] of [
    ['/kerml/level-crossing', 'language "kerml"', 'Level crossing - SysML v2 - Semantifyr Live'],
    ['/gamma/level-crosing', 'example "level-crosing"', 'Level crossing - Gamma - Semantifyr Live'],
    ['/oxsts/crossroads?library=uml', 'library "uml"', 'Tutorial: Basics - OXSTS - Semantifyr Live'],
  ] as const) {
    test(`says the ${requested} was not found and opens its replacement`, async ({ ui }) => {
      const page = await ui.open(address)

      await expect.poll(async () => {
        return page.locator('.orientation.is-error').innerText()
      }).toContain(`names the ${requested}`)
      await expect.poll(async () => {
        return page.title()
      }).toBe(opened)
    })
  }

  test('says a model of the fragment that cannot be read was not, and opens the example', async ({ ui }) => {
    const page = await ui.open('/oxsts/basics#model=garbled')

    await expect.poll(async () => {
      return page.locator('.orientation.is-error').innerText()
    }).toContain('The model in the link could not be read')
    await expect.poll(async () => {
      return page.title()
    }).toBe('Tutorial: Basics - OXSTS - Semantifyr Live')
  })

  test('opens a model of the fragment as a shared model', async ({ ui }) => {
    ui.backend.answer('discover', () => {
      return []
    })
    const model = await encodeCompressedBase64Url('package shared\n')
    const page = await ui.open(`/oxsts#model=${model}`)

    await expect.poll(async () => {
      return page.title()
    }).toBe('Shared model - OXSTS - Semantifyr Live')
    await expect.poll(async () => {
      return (await editor(page).lines.innerText()).replace(/\u00a0/g, ' ')
    }).toContain('package shared')
  })
})

describe('the history of a visit', () => {
  test('pushes a switch of example, language and flavour, and Back and Forward reopen each with its text', async ({ ui }) => {
    const page = await ui.open('/oxsts/basics')
    await casesListed(page)

    await chooseExample(page, 'Tutorial: Intermediate')
    await expect.poll(async () => {
      return pathOf(page)
    }).toBe('/oxsts/intermediate')
    await page.locator('.flavour-button').click()
    await page.getByRole('menuitem', { name: /With the Gamma library/ }).click()
    await expect.poll(async () => {
      return pathOf(page)
    }).toBe('/oxsts/level-crossing?library=gamma')
    await page.locator('.language-switch [role=group] button', { hasText: 'Gamma' }).click()
    await expect.poll(async () => {
      return pathOf(page)
    }).toBe('/gamma/level-crossing')
    await casesListed(page)

    const visited: string[] = []
    for (const move of ['back', 'back', 'back', 'forward'] as const) {
      await (move === 'back' ? page.goBack() : page.goForward())
      await expect.poll(async () => {
        return page.title()
      }).toMatch(/ - Semantifyr Live$/)
      visited.push(`${pathOf(page)} ${await page.title()}`)
    }

    expect(visited).toEqual([
      '/oxsts/level-crossing?library=gamma Level crossing (Gamma library) - OXSTS - Semantifyr Live',
      '/oxsts/intermediate Tutorial: Intermediate - OXSTS - Semantifyr Live',
      '/oxsts/basics Tutorial: Basics - OXSTS - Semantifyr Live',
      '/oxsts/intermediate Tutorial: Intermediate - OXSTS - Semantifyr Live',
    ])
    await expect.poll(async () => {
      return editor(page).lines.innerText()
    }).toContain('intermediate')
  })
})
