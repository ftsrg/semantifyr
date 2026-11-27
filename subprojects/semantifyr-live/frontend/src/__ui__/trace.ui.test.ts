/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Page } from 'playwright'
import { describe, expect } from 'vitest'

import { failing, LAMP_CASES, passing, type StatedCase } from './fake/defaults'
import { casesListed, editor, results, servedExample, statesOf, test, type Ui } from './harness'

// a Gamma run writes the case's Gamma trace beside its witness
function withGammaTrace(stated: StatedCase): StatedCase {
  if (stated.result.witnessUri === null) {
    return stated
  }
  const traceUri = stated.result.witnessUri.replace(/witness\.semantifyr$/, 'trace.get')
  const name = stated.id.split('::').at(-1) ?? stated.id
  const trace = `package lamp

trace ${name}Trace of ${name} {
    step {
        assert {
            state lamp.Main.Lit
        }
    }
}
`
  return { ...stated, result: { ...stated.result, traceUri }, documents: { ...stated.documents, [traceUri]: trace } }
}

const GAMMA_CASES = LAMP_CASES.map(withGammaTrace)

// named as the Semantifyr model the served SysML level crossing maps to names its cases
const SYSML_CASES = servedExample('sysml', 'level-crossing').cases.map((name, index) => {
  const id = `semantifyr::sysml::model::${name}`
  return index === 0 ? failing(id, index) : passing(id, index)
})

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
  for (const [address, views, cases] of [
    ['/language/level-crossing?library=gamma', ['steps', 'calls', 'witness'], LAMP_CASES],
    ['/language/level-crossing?library=sysml', ['steps', 'calls', 'witness'], LAMP_CASES],
    ['/gamma/level-crossing', ['own', 'steps', 'calls', 'witness'], GAMMA_CASES],
    ['/sysml/level-crossing', ['own', 'steps', 'calls', 'witness'], SYSML_CASES],
  ] as const) {
    test(`opens ${address} on its own view and shows each of its views`, async ({ ui }) => {
      ui.backend.cases = cases
      const page = await traced(ui, address)

      expect(await caseTrace(page).getAttribute('data-view')).toBe(views[0])
      for (const view of [...views.slice(1), views[0]]) {
        await showView(page, view)
      }
    })
  }
})

describe('the views of a trace', () => {
  test('open a text view in the editor as a read-only tab, which a middle click closes', async ({ ui }) => {
    ui.backend.cases = GAMMA_CASES
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
    ui.backend.answer('read', (request, stated) => {
      const text = stated()
      if (request.uri.endsWith('/witness.semantifyr')) {
        witness = text ?? ''
      }
      return text
    })
    const page = await traced(ui, '/language/level-crossing?library=gamma')
    await showView(page, 'witness')
    await caseTrace(page).locator('.frame-open').click()
    await expect.poll(async () => {
      return editor(page).selectedTab.innerText()
    }).toMatch(/\.witness\.semantifyr$/)
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

describe('the chosen view of a trace', () => {
  test('stays chosen for the case selected next', async ({ ui }) => {
    ui.backend.cases = GAMMA_CASES
    const page = await traced(ui, '/gamma/level-crossing')
    await showView(page, 'calls')
    const next = results(page).rows.and(page.locator(':not([aria-selected="true"])')).first()
    const name = await next.locator('.case-name').innerText()

    await next.click()

    await expect.poll(async () => {
      return results(page).trace.locator('.trace-title').innerText()
    }).toContain(name)
    expect(await caseTrace(page).getAttribute('data-view')).toBe('calls')
  })
})

describe('the text of a trace in its view', () => {
  test('stays shown after the extension host lets go of its document', async ({ ui }) => {
    ui.backend.cases = SYSML_CASES
    const page = await ui.open('/sysml/level-crossing')
    await page.clock.install()
    await casesListed(page)
    await results(page).verifyAll.click()
    const text = caseTrace(page).locator('.trace-editor .view-lines')
    await expect.poll(async () => {
      return text.innerText()
    }, { timeout: 60_000 }).toContain('Trace')

    await page.clock.fastForward('04:00')

    expect(await caseTrace(page).locator('.trace-editor .monaco-editor').count()).toBe(1)
    expect(await text.innerText()).toContain('Trace')
  })
})

describe('a trace text over a scrolled model', () => {
  test('opens as a tab with none of the model\'s sticky lines over it, and the model tab shows them again', async ({ ui }) => {
    ui.backend.cases = SYSML_CASES
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
    await expect.poll(async () => {
      return editor(page).lines.innerText()
    }, { timeout: 30_000 }).toContain('Trace')
    expect(await sticky.innerText()).toBe('')
    await editor(page).tabs.first().click()

    await expect.poll(async () => {
      return (await sticky.innerText()).replace(/\s+/g, ' ')
    }).toContain('port def Sensors')
  })
})

describe('the documents control', () => {
  test('filters the long list of the SysML library files', async ({ ui }) => {
    const page = await ui.open('/sysml/level-crossing')
    await casesListed(page)
    await page.getByRole('button', { name: 'Read-only documents' }).click()

    await page.locator('.documents-filter').fill('scalarvalues')

    await expect.poll(async () => {
      return page.locator('.documents-group[aria-label$="library"] .menu-item-label').allInnerTexts()
    }).toEqual(['ScalarValues.kerml'])
  })


  test('lists the documents of the selected case and follows the selection', async ({ ui }) => {
    ui.backend.cases = [
      withGammaTrace(failing('lamp::LampNeverLit', 1)),
      passing('lamp::LampCanLight', 2, null),
    ]
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
