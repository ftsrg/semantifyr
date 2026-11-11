/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Page } from 'playwright'
import { describe, expect } from 'vitest'

import { casesListed, editor, test } from './harness'

const BUILTIN = 'file:///workspace/.semantifyr/builtin/semantifyr.oxsts'
const TRACE_ANNOTATION_LINE = 78

// the text of the line the cursor is on in the document shown
async function currentLine(page: Page): Promise<string> {
  return page.evaluate(() => {
    const top = document.querySelector<HTMLElement>('section.editor .view-overlays .current-line')?.parentElement?.style.top
    const line = [...document.querySelectorAll<HTMLElement>('section.editor .view-line')].find((candidate) => {
      return candidate.style.top === top
    })
    return (line?.textContent ?? '').replace(/ /g, ' ').trim()
  })
}

describe('going to a definition', () => {
  test('in a library the session names opens the library\'s read-only tab at the element, and Back returns to the model', async ({ ui }) => {
    ui.backend.answer('feature', (request, recorded) => {
      if (request.method !== 'textDocument/definition') {
        return recorded()
      }
      const position = { line: TRACE_ANNOTATION_LINE, character: 11 }
      return [{ uri: BUILTIN, range: { start: position, end: position } }]
    })
    const page = await ui.open('/oxsts/basics')
    await casesListed(page)

    await page.locator('section.editor .view-line', { hasText: '@Trace' }).first().click()
    await page.keyboard.press('F12')

    await expect.poll(async () => {
      return editor(page).selectedTab.innerText()
    }).toContain('semantifyr.oxsts')
    await expect.poll(async () => {
      return currentLine(page)
    }).toBe('annotation Trace')
    await page.keyboard.press('Alt+ArrowLeft')
    await expect.poll(async () => {
      return editor(page).selectedTab.innerText()
    }).toContain('basics.oxsts')
  })
})

describe('the OXSTS of the model', () => {
  test('opens from the results head as a read-only tab that follows the edits of the model', async ({ ui }) => {
    const page = await ui.open('/gamma/level-crossing')
    await casesListed(page)
    ui.backend.answer('compiled', (request, recorded) => {
      const compiled = recorded()
      return request.text.includes('// edited') ? { ...compiled, oxsts: `${compiled.oxsts ?? ''}\n// recompiled\n` } : compiled
    })

    await page.locator('.results-header .model-oxsts-button').click()
    await expect.poll(async () => {
      return editor(page).selectedTab.innerText()
    }).toContain('level-crossing.oxsts')
    await expect.poll(async () => {
      return editor(page).lines.innerText()
    }).toContain('package')
    await editor(page).tabs.first().click()
    await editor(page).lines.click()
    await page.keyboard.press('Control+End')
    await page.keyboard.type('\n// edited\n')
    await editor(page).tabs.filter({ hasText: 'level-crossing.oxsts' }).click()
    await expect.poll(async () => {
      return page.locator('.readonly-banner').count()
    }).toBe(1)
    await editor(page).lines.click()

    await expect.poll(async () => {
      await page.keyboard.press('Control+End')
      return (await editor(page).lines.innerText()).replace(/\u00a0/g, ' ')
    }).toContain('// recompiled')
  })
})
