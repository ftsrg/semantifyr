/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { encodeCompressedBase64Url } from '@semantifyr/editor-common'
import { servedExamples } from '@semantifyr/editor-common/examples'
import type { Page } from 'playwright'
import { describe, expect } from 'vitest'

import { casesListed, editor, servedExample, test, titleOf } from './harness'

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
    }).toBe(titleOf('sysml', 'level-crossing'))
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
    }).toBe(titleOf('gamma', 'crossroads', 'oxsts'))
    expect((await ui.backend.openSession()).flavorId).toBe('oxsts-with-gamma-library')
    await expect.poll(async () => {
      return page.locator('.flavour-button').innerText()
    }).toContain('Gamma library')
    await casesListed(page)
  })
})

describe('an address that names what Live does not have', () => {
  for (const [address, requested, opened] of [
    ['/kerml/level-crossing', 'language "kerml"', titleOf('sysml', 'level-crossing')],
    ['/gamma/level-crosing', 'example "level-crosing"', titleOf('gamma', 'level-crossing')],
    ['/oxsts/crossroads?library=uml', 'library "uml"', titleOf('oxsts', 'basics')],
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
    }).toBe(titleOf('oxsts', 'basics'))
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

    await chooseExample(page, servedExample('oxsts', 'intermediate').label)
    await expect.poll(async () => {
      return pathOf(page)
    }).toBe('/oxsts/intermediate')
    await page.locator('.flavour-button').click()
    await page.getByRole('menuitem', { name: /Gamma's semantics/ }).click()
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
      `/oxsts/level-crossing?library=gamma ${titleOf('gamma', 'level-crossing', 'oxsts')}`,
      `/oxsts/intermediate ${titleOf('oxsts', 'intermediate')}`,
      `/oxsts/basics ${titleOf('oxsts', 'basics')}`,
      `/oxsts/intermediate ${titleOf('oxsts', 'intermediate')}`,
    ])
    await expect.poll(async () => {
      return editor(page).lines.innerText()
    }).toContain('intermediate')
  })
})

describe('the served examples', () => {
  for (const example of servedExamples) {
    test(`opens /${example.language}/${example.id} at its address, with no banner over it`, async ({ ui }) => {
      ui.backend.answer('discover', () => {
        return []
      })
      const page = await ui.open(`/${example.language}/${example.id}`)

      await expect.poll(async () => {
        return page.title()
      }).toBe(titleOf(example.language, example.id))
      expect(await page.locator('.orientation').count()).toBe(0)
    })
  }
})
