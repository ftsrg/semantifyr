/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Page } from 'playwright'
import { describe, expect } from 'vitest'

import { editorKeysOf } from '../testBrowser'
import { BUILTIN_LIBRARY, BUILTIN_LIBRARY_URI, lineOf, NEVER_LIT } from './fake/defaults'
import { casesListed, editor, results, statesOf, test } from './harness'

const TRACE_ANNOTATION_LINE = lineOf(BUILTIN_LIBRARY, 'annotation Trace')

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
    ui.backend.answer('feature', (request, stated) => {
      if (request.method !== 'textDocument/definition') {
        return stated()
      }
      const position = { line: TRACE_ANNOTATION_LINE, character: 11 }
      return [{ uri: BUILTIN_LIBRARY_URI, range: { start: position, end: position } }]
    })
    const page = await ui.open('/language/semantic-libraries')
    await casesListed(page)

    await page.locator('section.editor .view-line', { hasText: '@Trace' }).first().click()
    await page.keyboard.press('F12')

    await expect.poll(async () => {
      return editor(page).selectedTab.innerText()
    }).toContain('standard.semantifyr')
    await expect.poll(async () => {
      return currentLine(page)
    }).toBe('annotation Trace')
    await page.keyboard.press('Alt+ArrowLeft')
    await expect.poll(async () => {
      return editor(page).selectedTab.innerText()
    }).toContain('semantic-libraries.semantifyr')
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

const STATECHARTS_URI = 'file:///workspace/Library/Statecharts.semantifyr'

const STATECHARTS = `package semantifyr::gamma

class Component {
}

class Statechart : Component {
}
`

const COMPILED_TRACK = `package levelcrossing

class Track : Statechart {
}
`

describe('going to a definition from inside a read-only document', () => {
  test('of a library names the library file by its server address, sends no copy of it, and lands at the answer', async ({ ui }) => {
    const STRING_LINE = lineOf(BUILTIN_LIBRARY, 'extern datatype string')
    ui.backend.answer('feature', (request, stated) => {
      if (request.method !== 'textDocument/definition') {
        return stated()
      }
      const line = (request.params as DefinitionParams).textDocument.uri === BUILTIN_LIBRARY_URI ? STRING_LINE : TRACE_ANNOTATION_LINE
      const position = { line, character: 0 }
      return [{ uri: BUILTIN_LIBRARY_URI, range: { start: position, end: position } }]
    })
    const page = await ui.open('/language/semantic-libraries')
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
    expect(await editor(page).selectedTab.innerText()).toContain('standard.semantifyr')
    const session = ui.backend.session()
    expect(definitionsAsked(session)).toEqual(['file:///workspace/snippet.semantifyr', BUILTIN_LIBRARY_URI])
    expect(opened(session)).toEqual(['file:///workspace/snippet.semantifyr'])
  })

  test('of the OXSTS of a Gamma model names it under the derived address the session holds it by, and a library answer opens the library tab', async ({ ui }) => {
    ui.backend.compiled = { status: 'compiled', oxsts: COMPILED_TRACK, message: null }
    ui.backend.texts[STATECHARTS_URI] = STATECHARTS
    ui.backend.answer('feature', (request, stated) => {
      if (request.method !== 'textDocument/definition') {
        return stated()
      }
      const position = { line: lineOf(STATECHARTS, 'class Statechart : Component {'), character: 6 }
      return [{ uri: STATECHARTS_URI, range: { start: position, end: position } }]
    })
    const page = await ui.open('/gamma/level-crossing', { height: 1600 })
    await casesListed(page)
    await page.locator('.results-header .model-oxsts-button').click()
    await expect.poll(async () => {
      return page.locator('section.editor .view-line', { hasText: 'class Track : Statechart' }).count()
    }).toBe(1)

    await page.locator('section.editor .view-line', { hasText: 'class Track : Statechart' }).click()
    await page.keyboard.press('F12')

    await expect.poll(async () => {
      return editor(page).selectedTab.innerText()
    }).toContain('Statecharts.semantifyr')
    await expect.poll(async () => {
      return currentLine(page)
    }).toBe('class Statechart : Component {')
    const session = ui.backend.session()
    const derived = definitionsAsked(session)
    expect(derived).toHaveLength(1)
    expect(derived[0]).toMatch(/^file:\/\/\/workspace\/\.live-derived\/.*\.semantifyr$/)
    expect(opened(session)).toContain(derived[0])
  })
})

describe('the OXSTS of the model', () => {
  test('opens from the results head as a read-only tab that follows the edits of the model', async ({ ui }) => {
    const page = await ui.open('/gamma/level-crossing')
    await casesListed(page)
    ui.backend.answer('compiled', (request, stated) => {
      const compiled = stated()
      return request.text.includes('// edited') ? { ...compiled, oxsts: `${compiled.oxsts ?? ''}\n// recompiled\n` } : compiled
    })

    await page.locator('.results-header .model-oxsts-button').click()
    await expect.poll(async () => {
      return editor(page).selectedTab.innerText()
    }).toContain('level-crossing.semantifyr')
    await expect.poll(async () => {
      return editor(page).lines.innerText()
    }).toContain('package')
    await editor(page).tabs.first().click()
    await editor(page).lines.click()
    await page.keyboard.press((await editorKeysOf(page)).endOfDocument)
    await page.keyboard.type('\n// edited\n')
    await editor(page).tabs.filter({ hasText: 'level-crossing.semantifyr' }).click()
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

describe('the margin', () => {
  test('shows the marks of the shown document\'s cases alone, one state each, after another shell\'s editor showed the same model', async ({ ui }) => {
    const page = await ui.open('/language/level-crossing?library=gamma', { height: 5800 })
    await casesListed(page)
    await results(page).row(NEVER_LIT).getByRole('button', { name: `Run ${NEVER_LIT}` }).click()
    await expect.poll(async () => {
      return statesOf(editor(page).marks, 'verdict-glyph-')
    }).toEqual(['failed', 'not-run'])

    await page.setViewportSize({ width: 390, height: 844 })
    await expect.poll(async () => {
      return page.getByTestId('phone-shell').count()
    }).toBe(1)
    await page.setViewportSize({ width: 1440, height: 5800 })
    await expect.poll(async () => {
      return page.getByTestId('desktop-shell').count()
    }).toBe(1)

    await expect.poll(async () => {
      return statesOf(editor(page).marks, 'verdict-glyph-')
    }).toEqual(['failed', 'not-run'])
  })
})

describe('the starting editor', () => {
  test('leaves the focus in a menu opened before it started, and the menu open', async ({ ui }) => {
    let release = (): void => {}
    const loaded = new Promise<void>((resolve) => {
      release = resolve
    })
    const page = await ui.open('/language/modeling-and-verification', { held: { url: '**/assets/monaco-vscode-editor-service-override*', until: loaded } })
    const trigger = page.locator('.example-button')
    const inMenu = async (): Promise<boolean> => {
      return page.evaluate(() => {
        return (document.activeElement?.closest('[role="menu"]') ?? null) !== null
      })
    }
    await trigger.click()
    await expect.poll(inMenu).toBe(true)
    expect(await page.locator('section.editor .view-line').count(), 'the editor has not started').toBe(0)
    release()

    // the status bar shows the cursor only once the editor has started, after it would have taken the focus
    await expect.poll(async () => {
      return {
        started: (await page.locator('.statusbar').innerText()).includes('Ln 1, Col 1'),
        expanded: await trigger.getAttribute('aria-expanded'),
        inMenu: await inMenu(),
      }
    }).toEqual({ started: true, expanded: 'true', inMenu: true })
  })
})
