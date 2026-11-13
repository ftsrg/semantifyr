/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Page } from 'playwright'
import { describe, expect } from 'vitest'

import { casesListed, editor, results, statesOf, test, type Ui } from './harness'

const VIEW_CONTENT: Readonly<Record<string, string>> = {
  steps: '[data-testid="trace-steps"]',
  calls: '[data-testid="trace-calls"], [data-testid="trace-no-calls"]',
  own: '.trace-frame .trace-editor:not(.is-loading)',
  witness: '.trace-frame .trace-editor:not(.is-loading)',
}

// verifies every case and waits for the trace of the case selected after the run
async function traced(ui: Ui, address: string): Promise<Page> {
  const page = await ui.open(address)
  await casesListed(page)
  await results(page).verifyAll.click()
  await expect.poll(async () => {
    return (await statesOf(results(page).rows, 'status-')).every((state) => {
      return state === 'passed' || state === 'failed'
    })
  }, { timeout: 60_000 }).toBe(true)
  await expect.poll(async () => {
    return page.locator('section.trace .case-trace').count()
  }).toBe(1)
  return page
}

// the editor renders only the lines in view, each at its line's place, so they are read in the order of their place
async function shownLines(page: Page): Promise<string[]> {
  return page.evaluate(() => {
    const lines = document.querySelector('section.editor .view-lines')
    return [...(lines?.querySelectorAll<HTMLElement>('.view-line') ?? [])].sort((first, second) => {
      return parseFloat(first.style.top) - parseFloat(second.style.top)
    }).map((line) => {
      return line.textContent.replaceAll('\u00a0', ' ')
    })
  })
}

function caseTrace(page: Page) {
  return page.locator('section.trace .case-trace')
}

async function showView(page: Page, view: string): Promise<void> {
  await caseTrace(page).locator(`.trace-views button[data-view="${view}"]`).click()
  await expect.poll(async () => {
    return caseTrace(page).getAttribute('data-view')
  }).toBe(view)
  await expect.poll(async () => {
    return caseTrace(page).locator(VIEW_CONTENT[view]!).count()
  }, { timeout: 30_000 }).toBeGreaterThan(0)
  expect(await caseTrace(page).locator('.trace-hint', { hasText: 'could not be read' }).count()).toBe(0)
}

describe('the trace of each language', () => {
  for (const [address, views] of [
    ['/oxsts/level-crossing?library=gamma', ['steps', 'calls', 'witness']],
    ['/oxsts/level-crossing?library=sysml', ['steps', 'calls', 'witness']],
    ['/gamma/level-crossing', ['own', 'steps', 'calls', 'witness']],
    ['/sysml/level-crossing', ['own', 'steps', 'calls', 'witness']],
  ] as const) {
    test(`opens ${address} on its own view and shows each of its views`, async ({ ui }) => {
      const page = await traced(ui, address)

      expect(await caseTrace(page).getAttribute('data-view')).toBe(views[0])
      for (const view of [...views.slice(1), views[0]]) {
        await showView(page, view)
      }
    })
  }
})

describe('the views of a trace', () => {
  test('keep the view chosen for the language when another case is selected', async ({ ui }) => {
    const page = await traced(ui, '/gamma/level-crossing')
    await showView(page, 'calls')

    await results(page).rows.nth(1).click()

    await expect.poll(async () => {
      return results(page).rows.nth(1).getAttribute('aria-selected')
    }).toBe('true')
    expect(await caseTrace(page).getAttribute('data-view')).toBe('calls')
  })

  test('open a text view in the editor as a read-only tab, which a middle click closes', async ({ ui }) => {
    const page = await traced(ui, '/gamma/level-crossing')

    await caseTrace(page).locator('.frame-open').click()

    await expect.poll(async () => {
      return editor(page).selectedTab.innerText()
    }).toMatch(/\.trace\.get$/)
    const tab = editor(page).selectedTab
    await tab.click({ button: 'middle' })
    await expect.poll(async () => {
      return editor(page).tabs.allInnerTexts()
    }).toEqual([expect.stringMatching(/level-crossing\.gamma/)])
  })

  test('keep an open text tab readable once the server no longer has its document', async ({ ui }) => {
    let witness = ''
    ui.backend.answer('read', (request, recorded) => {
      const text = recorded()
      if (request.uri.endsWith('/witness.oxsts')) {
        witness = text ?? ''
      }
      return text
    })
    const page = await traced(ui, '/oxsts/level-crossing?library=gamma')
    await showView(page, 'witness')
    await caseTrace(page).locator('.frame-open').click()
    await expect.poll(async () => {
      return editor(page).selectedTab.innerText()
    }).toMatch(/\.witness\.oxsts$/)
    ui.backend.answer('read', () => {
      return null
    })

    await editor(page).tabs.first().click()
    await editor(page).tabs.nth(1).click()

    expect(witness).toContain('witness of')
    await expect.poll(async () => {
      const shown = await shownLines(page)
      return shown.length > 0 && shown.join('\n') === witness.split('\n').slice(0, shown.length).join('\n')
    }).toBe(true)
  })
})

describe('a trace text over a scrolled model', () => {
  test('opens as a tab with none of the model\'s sticky lines over it, and the model tab shows them again', async ({ ui }) => {
    const page = await traced(ui, '/sysml/level-crossing')
    const sticky = page.locator('section.editor .sticky-widget')
    await page.locator('section.editor .view-line', { hasText: 'port def Sensors {' }).first().click()
    await page.keyboard.press('PageDown')
    await expect.poll(async () => {
      return (await sticky.innerText()).replace(/\s+/g, ' ')
    }, { timeout: 30_000 }).not.toBe('')

    await caseTrace(page).locator('.frame-open').click()
    await expect.poll(async () => {
      return editor(page).selectedTab.innerText()
    }).toMatch(/Trace\.sysml$/)
    await page.waitForTimeout(1000)
    expect(await sticky.innerText()).toBe('')
    await editor(page).tabs.first().click()

    await expect.poll(async () => {
      return (await sticky.innerText()).replace(/\s+/g, ' ')
    }).toContain('port def Sensors')
  })
})

describe('the documents control', () => {
  test('lists the documents of the selected case and follows the selection', async ({ ui }) => {
    const page = await traced(ui, '/gamma/level-crossing')
    const entries = async (): Promise<string[]> => {
      await page.getByRole('button', { name: 'Read-only documents' }).click()
      const listed = await page.locator('.documents-group[aria-label="Selected case"] .menu-item').evaluateAll((items: Element[]) => {
        return items.map((item) => {
          return `${item.textContent}${(item as HTMLButtonElement).disabled ? ' (disabled)' : ''}`
        })
      })
      await page.keyboard.press('Escape')
      return listed
    }
    const first = await entries()

    await results(page).rows.nth(1).click()
    await expect.poll(async () => {
      return results(page).rows.nth(1).getAttribute('aria-selected')
    }).toBe('true')

    const second = await entries()
    expect(first.join()).toContain('Trace of the selected case')
    expect(second).not.toEqual(first)
  })
})
