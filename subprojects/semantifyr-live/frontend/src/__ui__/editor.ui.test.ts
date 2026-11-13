/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Page } from 'playwright'
import { describe, expect } from 'vitest'

import { editorKeysOf } from '../testBrowser'
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
    return (line?.textContent ?? '').replace(/\u00a0/g, ' ').trim()
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

interface DefinitionParams {
  textDocument: { uri: string }
}

function definitionsAsked(session: { requests: (method: string) => unknown[] }): string[] {
  return session.requests('textDocument/definition').map((params) => {
    return (params as DefinitionParams).textDocument.uri
  })
}

function opened(session: { received: unknown[] }): string[] {
  return session.received.filter((message) => {
    return (message as { method?: string }).method === 'textDocument/didOpen'
  }).map((message) => {
    return (message as { params: { textDocument: { uri: string } } }).params.textDocument.uri
  })
}

describe('going to a definition from inside a read-only document', () => {
  test('of a library names the library file by its server address, sends no copy of it, and lands at the answer', async ({ ui }) => {
    const STRING_LINE = 34
    ui.backend.answer('feature', (request, recorded) => {
      if (request.method !== 'textDocument/definition') {
        return recorded()
      }
      const line = (request.params as DefinitionParams).textDocument.uri === BUILTIN ? STRING_LINE : TRACE_ANNOTATION_LINE
      const position = { line, character: 0 }
      return [{ uri: BUILTIN, range: { start: position, end: position } }]
    })
    const page = await ui.open('/oxsts/basics')
    await casesListed(page)
    await page.locator('section.editor .view-line', { hasText: '@Trace' }).first().click()
    await page.keyboard.press('F12')
    await expect.poll(async () => {
      return currentLine(page)
    }).toBe('annotation Trace')

    await page.locator('section.editor .view-line', { hasText: 'annotation Trace' }).first().click()
    await page.keyboard.press('F12')

    await expect.poll(async () => {
      return currentLine(page)
    }).toBe('extern datatype string')
    expect(await editor(page).selectedTab.innerText()).toContain('semantifyr.oxsts')
    const session = ui.backend.session()
    expect(definitionsAsked(session)).toEqual(['file:///workspace/snippet.oxsts', BUILTIN])
    expect(opened(session)).toEqual(['file:///workspace/snippet.oxsts'])
  })

  test('of the OXSTS of a Gamma model names it under the derived address the session holds it by, and a library answer opens the library tab', async ({ ui }) => {
    const STATECHARTS = 'file:///workspace/Library/Statecharts.oxsts'
    ui.backend.answer('feature', (request, recorded) => {
      if (request.method !== 'textDocument/definition') {
        return recorded()
      }
      const position = { line: 48, character: 6 }
      return [{ uri: STATECHARTS, range: { start: position, end: position } }]
    })
    const page = await ui.open('/gamma/level-crossing', { height: 1600 })
    await casesListed(page)
    await page.locator('.results-header .model-oxsts-button').click()
    await expect.poll(async () => {
      return page.locator('section.editor .view-line', { hasText: 'class _Track : Statechart' }).count()
    }).toBe(1)

    await page.locator('section.editor .view-line', { hasText: 'class _Track : Statechart' }).click()
    await page.keyboard.press('F12')

    await expect.poll(async () => {
      return editor(page).selectedTab.innerText()
    }).toContain('Statecharts.oxsts')
    await expect.poll(async () => {
      return currentLine(page)
    }).toBe('class Statechart : Component {')
    const session = ui.backend.session()
    const derived = definitionsAsked(session)
    expect(derived).toHaveLength(1)
    expect(derived[0]).toMatch(/^file:\/\/\/workspace\/\.live-derived\/.*\.oxsts$/)
    expect(opened(session)).toContain(derived[0])
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
    await page.keyboard.press((await editorKeysOf(page)).endOfDocument)
    await page.keyboard.type('\n// edited\n')
    await editor(page).tabs.filter({ hasText: 'level-crossing.oxsts' }).click()
    await expect.poll(async () => {
      return page.locator('.readonly-banner').count()
    }).toBe(1)
    await editor(page).lines.click()

    await expect.poll(async () => {
      await page.keyboard.press((await editorKeysOf(page)).endOfDocument)
      return (await editor(page).lines.innerText()).replace(/\u00a0/g, ' ')
    }).toContain('// recompiled')
  })
})
