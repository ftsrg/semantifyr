/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect } from 'vitest'

import { casesListed, editor, test } from './harness'

const BUILTIN = 'file:///workspace/.semantifyr/builtin/semantifyr.oxsts'
const TRACE_ANNOTATION_LINE = 78

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
      return page.locator('.statusbar').innerText()
    }).toContain(`Ln ${TRACE_ANNOTATION_LINE + 1},`)
    await page.keyboard.press('Alt+ArrowLeft')
    await expect.poll(async () => {
      return editor(page).selectedTab.innerText()
    }).toContain('basics.oxsts')
  })
})
