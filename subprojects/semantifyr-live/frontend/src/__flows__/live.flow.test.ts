/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Browser, Page } from 'playwright'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'

import { launch, LIVE_URL, openPage, SLOW, token, verifyAll } from './browser'

// Goal: what a visitor does on the running image works end to end in a real browser: the bare address opens SysML v2,
// Verify all gives every case a verdict and opens a trace, the editor's language features answer, and the embed grows with its trace

let browser: Browser

beforeAll(async () => {
  browser = await launch()
})

afterAll(async () => {
  await browser.close()
})

function results(page: Page) {
  return {
    cases: page.locator('.results .case-row'),
    verifyAll: page.locator('.results').getByRole('button', { name: 'Verify all', exact: true }),
    trace: page.locator('section.trace .case-trace'),
  }
}

async function expectTraceViews(page: Page, firstView: string, otherView: string, otherContent: string): Promise<void> {
  const { trace } = results(page)
  await expect.poll(async () => {
    return trace.getAttribute('data-view')
  }, { timeout: 30_000 }).toBe(firstView)
  await trace.locator(`.trace-views button[data-view="${otherView}"]`).click()
  await expect.poll(async () => {
    return page.locator(otherContent).isVisible()
  }, { timeout: 30_000 }).toBe(true)
}

describe('SysML v2 on the bare address', () => {
  let page: Page

  beforeAll(async () => {
    page = await openPage(browser, '/')
  })

  it('opens the level crossing', async () => {
    await expect.poll(async () => {
      return page.title()
    }, { timeout: SLOW }).toBe('Level crossing - SysML v2 - Semantifyr Live')
    await expect.poll(async () => {
      return results(page).cases.count()
    }, { timeout: SLOW }).toBeGreaterThan(0)
  })

  it('hovers a name, goes to its definition, and opens a library element in its read-only tab', async () => {
    await token(page, 'port sensors : ~Sensors;', 'Sensors').hover()
    await expect.poll(async () => {
      return page.locator('.monaco-hover').filter({ visible: true }).innerText()
    }, { timeout: 30_000 }).toContain('Sensors')

    await token(page, 'port sensors : ~Sensors;', 'Sensors').click()
    await page.keyboard.press('F12')
    await expect.poll(async () => {
      return page.locator('.statusbar').innerText()
    }, { timeout: 30_000 }).toContain('Ln 12,')

    await token(page, 'private import ScalarValues::Integer;', 'Integer').click()
    await page.keyboard.press('F12')
    const libraryTab = page.locator('.tabs [role="tab"]', { hasText: 'ScalarValues.kerml' })
    await expect.poll(async () => {
      return libraryTab.getAttribute('aria-selected')
    }, { timeout: 30_000 }).toBe('true')
    await page.locator('.tabs [role="tab"]').first().click()
  })

  it('verifies every case and opens the first trace on the SysML trace text', async () => {
    const { cases, verifyAll: button } = results(page)
    await verifyAll(cases, button)
    await expectTraceViews(page, 'own', 'steps', '[data-testid="trace-steps"]')
  })

  it('completes a name of the model', async () => {
    await token(page, 'port controller : TrackReports;', 'TrackReports').click()
    await page.keyboard.press('End')
    await page.keyboard.press('Enter')
    await page.keyboard.type('port probe : Sens')
    await page.keyboard.press('Control+Space')
    await expect.poll(async () => {
      return page.locator('.suggest-widget .monaco-list-row').allInnerTexts()
    }, { timeout: 30_000 }).toEqual(expect.arrayContaining([expect.stringContaining('Sensors')]))
    await page.keyboard.press('Escape')
  })
})

describe('the SysML trace text as a tab', () => {
  it('opens over a scrolled model with nothing of the model drawn over it', async () => {
    const page = await openPage(browser, '/sysml/level-crossing')
    const { cases, verifyAll: button, trace } = results(page)
    await verifyAll(cases, button)
    await expect.poll(async () => {
      return trace.locator('.frame-open').isVisible()
    }, { timeout: 30_000 }).toBe(true)
    const stickyLines = page.locator('section.editor .sticky-widget')
    await token(page, 'port def Sensors {', 'Sensors').click()
    await page.keyboard.press('PageDown')
    await expect.poll(async () => {
      return stickyLines.innerText()
    }, { timeout: 30_000 }).not.toBe('')
    await trace.locator('.frame-open').click()
    const traceTab = page.locator('.tabs [role="tab"]', { hasText: 'Trace.sysml' })
    await expect.poll(async () => {
      return traceTab.getAttribute('aria-selected')
    }, { timeout: 30_000 }).toBe('true')
    await page.waitForTimeout(1000)
    expect(await stickyLines.innerText()).toBe('')
    await page.locator('.tabs [role="tab"]').first().click()
    await expect.poll(async () => {
      // the editor draws spaces as no-break spaces
      return (await stickyLines.innerText()).replace(/\s+/g, ' ')
    }, { timeout: 30_000 }).toContain('port def Sensors')
  })
})

describe('Gamma', () => {
  it('verifies every case of the level crossing and opens the first trace on the Gamma trace text', async () => {
    const page = await openPage(browser, '/gamma/level-crossing')
    const { cases, verifyAll: button } = results(page)

    await verifyAll(cases, button)

    await expectTraceViews(page, 'own', 'calls', '[data-testid="trace-calls"], [data-testid="trace-no-calls"]')
  })
})

describe('the verifying embed', () => {
  it('verifies in a frame and grows the frame with the trace it opens', async () => {
    const page = await browser.newPage({ viewport: { width: 900, height: 900 } })
    await page.goto(`${LIVE_URL}/api/health`)
    await page.setContent(`
      <iframe src="${LIVE_URL}/gamma/level-crossing?view=embed" style="width: 760px; height: 600px; border: 0"></iframe>
      <script>
        window.heights = []
        window.addEventListener('message', (event) => {
          if (event.data && event.data.type === 'semantifyr-live:height') {
            window.heights.push(event.data.height)
          }
        })
      </script>
    `)
    const heights = async (): Promise<number[]> => {
      return page.evaluate(() => {
        return (window as unknown as { heights: number[] }).heights
      })
    }
    const frame = page.frameLocator('iframe')
    const cases = frame.locator('.embed-case')

    await verifyAll(cases, frame.getByRole('button', { name: 'Verify all', exact: true }))
    const withTrace = frame.locator('.embed-case.has-trace').first()
    if (await withTrace.getAttribute('aria-expanded') === 'true') {
      await withTrace.click()
    }
    await expect.poll(async () => {
      return frame.locator('.embed-trace').count()
    }, { timeout: 30_000 }).toBe(0)
    await expect.poll(async () => {
      return (await heights()).at(-1)
    }, { timeout: 30_000 }).toBeDefined()
    const before = (await heights()).at(-1) ?? 0
    await withTrace.click()

    await expect.poll(async () => {
      return frame.locator('.embed-trace .case-trace').isVisible()
    }, { timeout: 30_000 }).toBe(true)
    await expect.poll(async () => {
      return (await heights()).at(-1) ?? 0
    }, { timeout: 30_000 }).toBeGreaterThan(before)
  })
})
