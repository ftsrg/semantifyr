/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Browser, Page } from 'playwright'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'

import { cursorAt, launch, openPage, shownPlace, SLOW, takePageErrors, verifyAll } from './browser'

// Goal: going to the definition of a library element opens the library's read-only tab at that element, from every language and flavour,
// from the model and from the read-only OXSTS and library documents, and Back returns

let browser: Browser

beforeAll(async () => {
  browser = await launch()
})

afterAll(async () => {
  await browser.close()
})

afterEach(() => {
  expect(takePageErrors(), 'uncaught errors in the page').toEqual([])
})

async function opened(path: string): Promise<Page> {
  const page = await openPage(browser, path)
  await expect.poll(async () => {
    return page.locator('.results .case-row').count()
  }, { timeout: SLOW }).toBeGreaterThan(0)
  return page
}

// goes to the definition of what the cursor is on, at `offset` characters into `text`, and expects the tab and the line it lands on
async function goesTo(page: Page, text: string, offset: number, tab: string, line: string): Promise<void> {
  await cursorAt(page, text, offset)
  await page.keyboard.press('F12')
  await expect.poll(async () => {
    return shownPlace(page)
  }, { timeout: 30_000 }).toEqual({ tab, line })
}

async function back(page: Page, tab: string, line: string): Promise<void> {
  await page.keyboard.press('Alt+ArrowLeft')
  await expect.poll(async () => {
    return shownPlace(page)
  }, { timeout: 30_000 }).toEqual({ tab, line })
}

async function openDocument(page: Page, entry: string): Promise<void> {
  await page.getByRole('button', { name: 'Read-only documents' }).click()
  await page.locator('.documents-menu .menu-item', { hasText: entry }).first().click()
}

describe('going to a definition in a library', () => {
  it('opens the builtin library from plain OXSTS, and Back returns to the model', async () => {
    const page = await opened('/oxsts/basics')
    await goesTo(page, '@Trace', 1, 'semantifyr.oxsts', 'annotation Trace')
    await goesTo(page, 'Tag(category: string)', 15, 'semantifyr.oxsts', 'extern datatype string')
    await back(page, 'semantifyr.oxsts', 'annotation Tag(category: string)')
    await back(page, 'basics.oxsts', '@Trace')
    await page.locator('section.editor .view-line', { hasText: '@VerificationCase' }).first().locator('span > span', { hasText: 'VerificationCase' }).last().click({ modifiers: ['Control'] })
    await expect.poll(async () => {
      return shownPlace(page)
    }, { timeout: 30_000 }).toEqual({ tab: 'semantifyr.oxsts', line: 'annotation VerificationCase(summary: string[0..1])' })
    await page.close()
  })

  it('opens the Gamma library from OXSTS, goes on from one library file into another, and Back returns', async () => {
    const page = await opened('/oxsts/level-crossing?library=gamma')
    await goesTo(page, ': Statechart', 2, 'Statecharts.oxsts', 'class Statechart : Component {')
    await goesTo(page, 'Statechart : Component', 13, 'Components.oxsts', 'abstract class Component {')
    await back(page, 'Statecharts.oxsts', 'class Statechart : Component {')
    await page.locator('.tabs [role="tab"]').first().click()
    await goesTo(page, '@Shared', 1, 'semantifyr.oxsts', 'annotation Shared')
    await page.close()
  })

  it('opens the SysML v2 semantic library from OXSTS', async () => {
    const page = await opened('/oxsts/level-crossing?library=sysml')
    await goesTo(page, ': Item', 2, 'Items.oxsts', 'class Item')
    await goesTo(page, 'semantifyr::sysml::items', 0, 'Items.oxsts', 'package semantifyr::sysml::items')
    await page.close()
  })

  it('opens the Gamma library from the OXSTS of a Gamma model, which then still verifies', async () => {
    const page = await opened('/gamma/level-crossing')
    await openDocument(page, 'OXSTS of the model')
    await expect.poll(async () => {
      return page.locator('section.editor .view-lines').innerText()
    }, { timeout: 30_000 }).toContain('Statechart')
    await goesTo(page, ': Statechart', 2, 'Statecharts.oxsts', 'class Statechart : Component {')
    await page.locator('.tabs [role="tab"]', { hasText: 'level-crossing.oxsts' }).click()
    await goesTo(page, '@Shared', 1, 'semantifyr.oxsts', 'annotation Shared')
    await page.locator('.tabs [role="tab"]').first().click()
    await verifyAll(page.locator('.results .case-row'), page.locator('.results').getByRole('button', { name: 'Verify all', exact: true }))
    await page.close()
  })

  it('opens the SysML v2 standard library from SysML, goes on inside it, and opens the semantic library from the OXSTS of the model', async () => {
    const page = await opened('/sysml/level-crossing')
    await goesTo(page, 'ScalarValues::Integer', 14, 'ScalarValues.kerml', 'datatype Integer specializes Rational;')
    await goesTo(page, 'specializes Rational', 12, 'ScalarValues.kerml', 'datatype Rational specializes Real;')
    await back(page, 'ScalarValues.kerml', 'datatype Integer specializes Rational;')
    await page.locator('.tabs [role="tab"]').first().click()
    await goesTo(page, 'isStateActive(system.trackA', 0, 'Verification.sysml', 'calc def isStateActive {')
    await goesTo(page, 'ScalarValues::Boolean', 14, 'ScalarValues.kerml', 'datatype Boolean specializes ScalarValue;')
    await openDocument(page, 'OXSTS of the model')
    await expect.poll(async () => {
      return page.locator('section.editor .view-lines').innerText()
    }, { timeout: 30_000 }).toContain('Item')
    await goesTo(page, ': Item', 2, 'Items.oxsts', 'class Item')
    await page.close()
  })
})
