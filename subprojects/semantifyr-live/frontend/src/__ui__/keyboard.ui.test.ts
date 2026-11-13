/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Page } from 'playwright'
import { describe, expect } from 'vitest'

import { casesListed, test } from './harness'

// Goal: a keyboard alone reaches every control of the desktop shell and gets out of every editor, the full editor by the key its name tells

const FOCUSABLE = 'a[href], button, input, select, textarea, [tabindex]'

// every control the shell shows that Tab can reach, each named by its place in the document
async function reachableControls(page: Page): Promise<string[]> {
  return page.evaluate((selector) => {
    const controls = [...document.querySelectorAll<HTMLElement>(selector)].filter((element) => {
      const rectangle = element.getBoundingClientRect()
      return element.tabIndex >= 0
        && !element.matches(':disabled')
        && element.closest('[hidden], [inert], .monaco-editor') === null
        && rectangle.width > 0
        && rectangle.height > 0
        && getComputedStyle(element).visibility !== 'hidden'
    })
    return controls.map((element) => {
      return element.dataset.walkId ??= String([...document.querySelectorAll('*')].indexOf(element))
    })
  }, FOCUSABLE)
}

// the control the focus is on, the editor counting as one
async function focused(page: Page): Promise<string> {
  return page.evaluate(() => {
    const active = document.activeElement
    if (!(active instanceof HTMLElement) || active === document.body) {
      return 'page'
    }
    if (active.closest('.monaco-editor') !== null) {
      return 'editor'
    }
    return active.dataset.walkId ??= String([...document.querySelectorAll('*')].indexOf(active))
  })
}

async function editorLabel(page: Page): Promise<string> {
  return page.evaluate(() => {
    const active = document.activeElement
    return active?.closest('.monaco-editor') ? active.getAttribute('aria-label') ?? '' : ''
  })
}

// a caret inside a class shows the class's sticky header, a focus stop of its own between the text and the next control
const LEFT = /^(sticky header, )?(?!editor$|sticky header$)[^,]+$/

async function tabFromText(page: Page): Promise<string> {
  const stops: string[] = []
  for (let press = 0; press < 2; press++) {
    await page.keyboard.press('Tab')
    const onHeader = await page.evaluate(() => {
      return (document.activeElement?.closest('.sticky-widget-lines') ?? null) !== null
    })
    const place = onHeader ? 'sticky header' : await focused(page)
    stops.push(place)
    if (place !== 'sticky header') {
      break
    }
  }
  return stops.join(', ')
}

describe('the keyboard', () => {
  test('reaches every control of the desktop shell, leaving the model by the key the editor names', async ({ ui }) => {
    const page = await ui.open('/gamma/level-crossing')
    await casesListed(page)
    const controls = await reachableControls(page)
    expect(controls.length).toBeGreaterThan(10)

    const seen = new Set<string>()
    let freed = false
    for (let press = 0; press < controls.length + 20; press++) {
      const place = await focused(page)
      seen.add(place)
      if (place === 'editor' && !freed) {
        const label = await editorLabel(page)
        const key = /(Control\+(?:Shift\+)?M) switches Tab to leave the editor/.exec(label)?.[1]
        expect(key, `the editor names the key that lets Tab leave it: ${label}`).toBeDefined()
        await page.keyboard.press('Tab')
        expect(await focused(page), 'Tab indents in the model until the key is pressed').toBe('editor')
        await page.keyboard.press(key!)
        freed = true
      }
      await page.keyboard.press('Tab')
    }

    expect(seen.has('editor')).toBe(true)
    expect(controls.filter((control) => {
      return !seen.has(control)
    })).toEqual([])
  })

  test('leaves a read-only document by Tab', async ({ ui }) => {
    const page = await ui.open('/gamma/level-crossing')
    await casesListed(page)
    await page.getByRole('button', { name: 'Read-only documents' }).click()
    await page.locator('.documents-group:not([aria-label="Selected case"]):not([aria-label="Model"]) .menu-item').first().click()
    await expect.poll(async () => {
      return page.locator('.tabs [role="tab"][aria-selected="true"]').count()
    }).toBe(1)
    await page.locator('section.editor .view-lines').first().click()
    expect(await focused(page)).toBe('editor')

    expect(await tabFromText(page)).toMatch(LEFT)
  })

  for (const view of ['embed', 'edit-only']) {
    test(`leaves the editor of an embed by Tab, in the ${view} view`, async ({ ui }) => {
      const page = await ui.open(`/oxsts/level-crossing?library=gamma&view=${view}`)
      await expect.poll(async () => {
        return page.locator('.view-lines').first().innerText()
      }).toContain('package')
      // the embed sets the editor's options once its runtime starts, which may follow the first text
      await expect.poll(async () => {
        await page.locator('.view-lines').first().click()
        expect(await focused(page)).toBe('editor')
        return tabFromText(page)
      }).toMatch(LEFT)
    })
  }
})
